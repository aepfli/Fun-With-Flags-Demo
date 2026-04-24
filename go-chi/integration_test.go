// Integration test for the Go demo. Uses testcontainers-go to spin up a real
// flagd container, rewires the OpenFeature provider to RPC mode against it,
// and asserts that GET / returns the expected variant for both the default
// case and the German one.
//
// This is the "step 5.2" scenario: no separate `docker compose up` needed,
// the test owns the container's lifecycle.
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"

	flagdprovider "github.com/open-feature/go-sdk-contrib/providers/flagd/pkg"
	"github.com/open-feature/go-sdk/openfeature"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"

	"github.com/go-chi/chi/v5"

	"github.com/openfeature/fun-with-flags-demo/go-chi/internal/middleware"
)


func TestFlagdIntegration(t *testing.T) {
	ctx := context.Background()

	flagsPath, err := filepath.Abs("./flags.json")
	if err != nil {
		t.Fatalf("resolve flags.json: %v", err)
	}

	req := testcontainers.ContainerRequest{
		Image:        "ghcr.io/open-feature/flagd:latest",
		ExposedPorts: []string{"8013/tcp"},
		Cmd:          []string{"start", "--uri", "file:/flags.json"},
		Files: []testcontainers.ContainerFile{
			{HostFilePath: flagsPath, ContainerFilePath: "/flags.json", FileMode: 0o644},
		},
		WaitingFor: wait.ForListeningPort("8013/tcp").WithStartupTimeout(30 * time.Second),
	}
	c, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{ContainerRequest: req, Started: true})
	if err != nil {
		t.Fatalf("start flagd: %v", err)
	}
	defer func() { _ = c.Terminate(ctx) }()

	host, err := c.Host(ctx)
	if err != nil {
		t.Fatalf("host: %v", err)
	}
	mapped, err := c.MappedPort(ctx, "8013")
	if err != nil {
		t.Fatalf("port: %v", err)
	}

	provider, err := flagdprovider.NewProvider(
		flagdprovider.WithRPCResolver(),
		flagdprovider.WithHost(host),
		flagdprovider.WithPort(mapped.Num()),
	)
	if err != nil {
		t.Fatalf("build provider: %v", err)
	}
	if err := openfeature.SetProviderAndWait(provider); err != nil {
		t.Fatalf("set provider: %v", err)
	}

	client := openfeature.NewClient("fun-with-flags-go-test")

	r := chi.NewRouter()
	r.Use(middleware.Language)
	r.Get("/", func(w http.ResponseWriter, req *http.Request) {
		details, _ := client.StringValueDetails(req.Context(), "greetings", "Hello World", openfeature.EvaluationContext{})
		_ = json.NewEncoder(w).Encode(details)
	})

	cases := []struct {
		name  string
		query string
		want  string
	}{
		{"default", "", "Hello World!"},
		{"german", "?language=de", "Hallo Welt!"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			rr := httptest.NewRecorder()
			req := httptest.NewRequest("GET", "/"+tc.query, nil)
			r.ServeHTTP(rr, req)
			body := rr.Body.String()
			if !strings.Contains(body, tc.want) {
				t.Fatalf("body %q missing %q", body, tc.want)
			}
			var payload map[string]any
			if err := json.Unmarshal([]byte(body), &payload); err != nil {
				t.Fatalf("decode body: %v", err)
			}
			fmt.Printf("%s -> %v\n", tc.name, payload["Value"])
		})
	}
}
