package de.winniepat.smpwrapped.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRoutingTest {

    private Javalin app;
    private int port;

    @BeforeEach
    void setUp() {
        app = Javalin.create(config -> {
            config.jsonMapper(new io.javalin.json.JavalinJackson());
            config.showJavalinBanner = false;
            config.staticFiles.add("/webapp", io.javalin.http.staticfiles.Location.CLASSPATH);
            config.spaRoot.addFile("/", "/webapp/index.html", io.javalin.http.staticfiles.Location.CLASSPATH);
        });
        registerRoutes();
        app.start(0);
        port = app.port();
    }

    private void registerRoutes() {
        app.get("/api/v1/health", ctx -> ctx.json(Map.of("status", "ok")));

        app.get("/api/v1/seasons", ctx -> ctx.json(Map.of("seasons", List.of())));
        app.get("/api/v1/seasons/current", ctx -> ctx.json(Map.of("error", "No active season"))
            .status(HttpStatus.NOT_FOUND));
        app.get("/api/v1/seasons/{seasonId}/stats", ctx ->
            ctx.json(Map.of("route", "seasonStats", "seasonId", ctx.pathParam("seasonId"))));
        app.get("/api/v1/seasons/{seasonId}/leaderboards/{stat}", ctx ->
            ctx.json(Map.of("route", "leaderboard", "seasonId", ctx.pathParam("seasonId"),
                "stat", ctx.pathParam("stat"))));

        app.get("/api/v1/players/by-name/{playerName}", ctx -> {
            if (ctx.pathParam("playerName").equals("missing")) {
                ctx.json(Map.of("error", "Player not found")).status(HttpStatus.NOT_FOUND);
                return;
            }
            ctx.json(Map.of("route", "playerByName", "playerName", ctx.pathParam("playerName")));
        });
        app.get("/api/v1/players/{playerId}", ctx ->
            ctx.json(Map.of("route", "player", "playerId", ctx.pathParam("playerId"))));
        app.get("/api/v1/players/{playerId}/wrapped/{seasonId}", ctx ->
            ctx.json(Map.of("route", "playerWrapped", "playerId", ctx.pathParam("playerId"),
                "seasonId", ctx.pathParam("seasonId"))));
        app.get("/api/v1/players/{playerId}/stats/{seasonId}", ctx ->
            ctx.json(Map.of("route", "playerStats", "playerId", ctx.pathParam("playerId"),
                "seasonId", ctx.pathParam("seasonId"))));

        app.get("/", ctx -> ctx.html("<!DOCTYPE html><html></html>"));

        app.error(404, ctx -> {
            if (hasNoBody(ctx)) {
                ctx.json(Map.of("error", "Not found")).status(HttpStatus.NOT_FOUND);
            }
        });
        app.error(500, ctx -> {
            if (hasNoBody(ctx)) {
                ctx.json(Map.of("error", "Internal server error")).status(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        });
        app.exception(Exception.class, (exception, ctx) -> {
            if (hasNoBody(ctx)) {
                ctx.json(Map.of("error", "Internal server error")).status(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        });

        app.before(ctx -> {
            String path = ctx.path();
            if (path != null && path.startsWith("/api/")) {
                ctx.attribute("rateLimited", false);
            }
        });

        app.after(ctx -> rewriteHtmlApiResponses(ctx));
    }

    private static boolean hasNoBody(Context ctx) {
        String body = ctx.result();
        return body == null || body.isBlank();
    }

    private void rewriteHtmlApiResponses(Context ctx) {
        String path = ctx.path();
        if (path == null || !path.startsWith("/api/")) {
            return;
        }
        String body = ctx.result();
        if (body == null || body.isEmpty()) {
            return;
        }
        if (body.stripLeading().startsWith("<")) {
            ctx.json(Map.of("error", "Not found")).status(HttpStatus.NOT_FOUND);
        }
    }

    @AfterEach
    void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    @DisplayName("by-name lookups must not be shadowed by the player id route")
    void byNameRouteIsNotShadowed() {
        Response response = get("/api/v1/players/by-name/WinniePat");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("playerByName").contains("WinniePat");
    }

    @Test
    @DisplayName("by-name lookups work for any casing of the player name")
    void byNameRouteAcceptsAnyName() {
        for (String name : List.of("Notch", "jeb_", "some.player", "A123456789")) {
            Response response = get("/api/v1/players/by-name/" + name);
            assertThat(response.status()).as("name=%s", name).isEqualTo(200);
            assertThat(response.body()).contains("playerByName");
        }
    }

    @Test
    @DisplayName("The plain player id route still resolves")
    void playerIdRouteResolves() {
        Response response = get("/api/v1/players/4d537abc-e205-4258-b289-ac70d814b5db");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("\"route\":\"player\"");
    }

    @Test
    @DisplayName("Nested player routes still resolve")
    void nestedPlayerRoutesResolve() {
        Response wrapped = get("/api/v1/players/abc-123/wrapped/season1");
        assertThat(wrapped.status()).isEqualTo(200);
        assertThat(wrapped.body()).contains("playerWrapped").contains("season1");

        Response stats = get("/api/v1/players/abc-123/stats/season1");
        assertThat(stats.status()).isEqualTo(200);
        assertThat(stats.body()).contains("playerStats");
    }

    @Test
    @DisplayName("Season routes still resolve")
    void seasonRoutesResolve() {
        assertThat(get("/api/v1/seasons").status()).isEqualTo(200);
        assertThat(get("/api/v1/seasons/current").status()).isEqualTo(404);

        Response stats = get("/api/v1/seasons/season1/stats");
        assertThat(stats.status()).isEqualTo(200);
        assertThat(stats.body()).contains("seasonStats");

        Response board = get("/api/v1/seasons/season1/leaderboards/blocks_mined");
        assertThat(board.status()).isEqualTo(200);
        assertThat(board.body()).contains("leaderboard");
    }

    @Test
    @DisplayName("A bare by-name path is treated as a player id, not a name lookup")
    void bareByNameIsAPlayerId() {
        Response response = get("/api/v1/players/by-name");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("\"route\":\"player\"");
    }

    @Test
    @DisplayName("Deep unknown API routes return JSON instead of the SPA html")
    void deepUnknownApiRoutesReturnJson() {
        Response response = get("/api/v1/nope/nope/nope");

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.body()).contains("Not found");
    }

    @Test
    @DisplayName("The SPA fallback still serves html for browser routes")
    void spaFallbackStillServesHtml() {
        Response response = get("/w/WinniePat");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("<!DOCTYPE html>");
    }

    @Test
    @DisplayName("A handler 404 keeps its own error message")
    void handlerNotFoundKeepsItsMessage() {
        Response response = get("/api/v1/players/by-name/missing");

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.body()).contains("Player not found");
        assertThat(response.body()).doesNotContain("Not found");
    }

    @Test
    @DisplayName("A handler 404 is not replaced by the generic 404 body")
    void handlerNotFoundIsNotClobbered() {
        Response current = get("/api/v1/seasons/current");

        assertThat(current.status()).isEqualTo(404);
        assertThat(current.body()).contains("No active season");
        assertThat(current.body()).doesNotContain("Not found");
    }

    @Test
    @DisplayName("An unrouted path still gets the generic JSON 404")
    void unroutedPathGetsGeneric404() {
        Response response = get("/api/v1/nope/nope/nope");

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.body()).contains("Not found");
    }

    @Test
    @DisplayName("A route param is never able to reach the html fallback")
    void apiResponsesStayJson() {
        AtomicReference<String> body = new AtomicReference<>();
        get("/api/v1/players/by-name/abc");
        body.set("/api/v1/players/abc/wrapped/season1");

        Response response = get(body.get());
        assertThat(response.body()).startsWith("{");
    }

    private record Response(int status, String body) {
    }

    private Response get(String path) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + path)
                .toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int status = connection.getResponseCode();
            try (var stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                String body = stream == null ? "" : new String(stream.readAllBytes());
                return new Response(status, body);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Request failed: " + path, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}