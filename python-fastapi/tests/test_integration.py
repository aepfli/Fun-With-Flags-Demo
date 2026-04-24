"""Integration test — spins up flagd in a container and exercises the
OpenFeature client directly.

Mirrors the go-chi variant at ../../go-chi/integration_test.go: the test owns
flagd's lifecycle (no `docker compose up` needed) and asserts variant
resolution against the repo's flags.json. We intentionally do NOT import the
FastAPI app here — importing it triggers the lifespan which sets up a FILE-
mode flagd against ./flags.json and races with our container setup.
"""

from __future__ import annotations

import time
from pathlib import Path

import pytest
from openfeature import api
from openfeature.contrib.provider.flagd import FlagdProvider
from openfeature.contrib.provider.flagd.config import ResolverType
from openfeature.evaluation_context import EvaluationContext
from testcontainers.core.container import DockerContainer
from testcontainers.core.waiting_utils import wait_for_logs

# Absolute path so pytest's cwd doesn't matter.
FLAGS_FILE = Path(__file__).resolve().parent.parent / "flags.json"


@pytest.fixture(scope="module")
def flagd_container():
    """Start flagd in RPC mode with the repo's flags.json mounted read-only."""
    container = (
        DockerContainer("ghcr.io/open-feature/flagd:latest")
        .with_exposed_ports(8013)
        .with_volume_mapping(str(FLAGS_FILE), "/flags.json", mode="ro")
        .with_command("start --uri file:/flags.json")
    )
    container.start()
    try:
        # flagd log message varies slightly across builds; fall back to a short
        # sleep if we can't find our marker within the timeout.
        wait_for_logs(container, "listening", timeout=30)
    except Exception:
        time.sleep(2)
    yield container
    container.stop()


@pytest.fixture(scope="module")
def openfeature_client(flagd_container):
    """Point the global OpenFeature API at the containerised flagd."""
    host = flagd_container.get_container_host_ip()
    port = int(flagd_container.get_exposed_port(8013))
    api.set_provider(
        FlagdProvider(host=host, port=port, resolver_type=ResolverType.RPC)
    )
    client = api.get_client()
    yield client
    api.shutdown()


def test_default_greeting(openfeature_client):
    """No pythonVersion, no language → defaultVariant `hello` → "Hello World!"."""
    details = openfeature_client.get_string_details("greetings", "Hello World")
    assert details.value == "Hello World!"
    assert details.variant == "hello"


def test_german_greeting(openfeature_client):
    """`language=de` routes to the `hallo` variant via targeting rules."""
    ctx = EvaluationContext(attributes={"language": "de"})
    details = openfeature_client.get_string_details("greetings", "Hello World", ctx)
    assert details.value == "Hallo Welt!"
    assert details.variant == "hallo"
