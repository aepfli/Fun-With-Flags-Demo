"""Integration test — spins up flagd in a container, points the app at it via RPC.

This is the testcontainers-python equivalent of the Spring Boot `@Testcontainers`
integration test at step 5.2. The goal is the same: the test suite owns its
own flagd, so you don't have to remember to `docker compose up` first.
"""

from __future__ import annotations

import os
import time
from pathlib import Path

import httpx
import pytest
from testcontainers.core.container import DockerContainer
from testcontainers.core.waiting_utils import wait_for_logs

FLAGS_FILE = Path(__file__).resolve().parent.parent / "flags.json"


@pytest.fixture(scope="module")
def flagd_container():
    """Start flagd in RPC mode with the repo's flags.json mounted."""
    container = (
        DockerContainer("ghcr.io/open-feature/flagd:latest")
        .with_exposed_ports(8013)
        .with_volume_mapping(str(FLAGS_FILE), "/flags.json", mode="ro")
        .with_command("start --uri file:./flags.json")
    )
    container.start()
    try:
        # flagd logs "Flag sync listener started" once it's ready.
        wait_for_logs(container, "listening", timeout=30)
    except Exception:
        # Fall back to a small sleep — some flagd builds print different lines.
        time.sleep(2)

    os.environ["FLAGD_HOST"] = container.get_container_host_ip()
    os.environ["FLAGD_PORT"] = str(container.get_exposed_port(8013))
    yield container
    container.stop()


@pytest.fixture(scope="module")
def client(flagd_container):
    """Build an app that uses a remote flagd instead of the file provider."""
    # Import late so env vars are set before the app configures OpenFeature.
    from fastapi import FastAPI
    from openfeature import api
    from openfeature.contrib.provider.flagd import FlagdProvider
    from openfeature.contrib.provider.flagd.config import ResolverType
    from openfeature.evaluation_context import EvaluationContext
    from openfeature.transaction_context import ContextVarsTransactionContextPropagator

    from app.hook import CustomHook
    from app.main import hello_world
    from app.middleware import LanguageMiddleware
    from app.openfeature_setup import python_version_string

    api.set_provider(
        FlagdProvider(
            host=os.environ["FLAGD_HOST"],
            port=int(os.environ["FLAGD_PORT"]),
            resolver_type=ResolverType.RPC,
        )
    )
    api.set_transaction_context_propagator(ContextVarsTransactionContextPropagator())
    api.clear_hooks()
    api.add_hooks([CustomHook()])
    api.set_evaluation_context(
        EvaluationContext(attributes={"pythonVersion": python_version_string()})
    )

    app = FastAPI()
    app.add_middleware(LanguageMiddleware)
    app.get("/")(hello_world)

    transport = httpx.ASGITransport(app=app)
    with httpx.Client(transport=transport, base_url="http://testserver") as c:
        yield c

    api.shutdown()


def test_default_returns_pythonista(client: httpx.Client) -> None:
    """sem_ver targeting picks `pythonista` for any Python 3.10+."""
    response = client.get("/")
    assert response.status_code == 200
    body = response.json()
    assert body["variant"] == "pythonista"
    assert body["value"] == "Hi pythonista"


def test_language_de_still_beaten_by_sem_ver(client: httpx.Client) -> None:
    """Yes — on 3.10+ the sem_ver branch wins even with `?language=de`.

    This is faithful to the targeting rule in flags.json: the sem_ver check
    comes first in the `if`, so it short-circuits before language is checked.
    If you're running the test on Python <3.10 you'll get `hallo` instead.
    """
    import sys

    response = client.get("/?language=de")
    assert response.status_code == 200
    body = response.json()
    if sys.version_info >= (3, 10):
        assert body["variant"] == "pythonista"
    else:
        assert body["variant"] == "hallo"
        assert body["value"] == "Hallo Welt!"
