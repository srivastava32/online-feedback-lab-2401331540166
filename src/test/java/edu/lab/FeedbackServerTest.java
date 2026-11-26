package edu.lab;

import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackServerTest {
    private static HttpServer server;
    private static HttpClient client;
    private static String base;
    private static final String VALID = "name=Lab+Student&email=student%40example.com"
            + "&category=Suggestion&rating=5&comments=Clear+and+simple";

    @BeforeAll
    static void start() throws Exception {
        server = FeedbackServer.createServer(0);
        server.start();
        client = HttpClient.newHttpClient();
        base = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stop() {
        client.close();
        server.stop(0);
    }

    private HttpResponse<String> post(String body, String origin, String type) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + "/feedback"))
                .header("Origin", origin).header("Content-Type", type)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String body) throws Exception {
        return post(body, base, "application/x-www-form-urlencoded");
    }

    @Test
    void servesFormAndSecurityHeaders() throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(base)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("<form action=\"/feedback\" method=\"post\">"));
        for (String field : new String[]{"name", "email", "category", "rating", "comments"}) {
            assertTrue(response.body().contains("name=\"" + field + "\""));
        }
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
        assertEquals("same-origin", response.headers().firstValue("Referrer-Policy").orElseThrow());
        assertTrue(response.headers().firstValue("Content-Security-Policy").orElseThrow().contains("form-action 'self'"));
        assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    @Test
    void validFeedbackReceivesConfirmation() throws Exception {
        var response = post(VALID);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Feedback received"));
        assertFalse(response.body().contains("student@example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name=", "email=invalid", "category=Unknown", "rating=0", "rating=6", "comments="})
    void rejectsInvalidFields(String replacement) throws Exception {
        String key = replacement.split("=", 2)[0];
        assertEquals(400, post(VALID.replaceAll(key + "=[^&]*", replacement)).statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"&rating=2", "&extra=value", "&broken", "&name=%ZZ"})
    void rejectsDuplicateUnknownAndMalformedFields(String extra) throws Exception {
        assertEquals(400, post(VALID + extra).statusCode());
    }

    @Test
    void rejectsMissingFieldsAndExcessLength() throws Exception {
        assertEquals(400, post("name=Student").statusCode());
        assertEquals(400, post(VALID.replace("Clear+and+simple", "a".repeat(2001))).statusCode());
        assertEquals(413, post("a".repeat(16_385)).statusCode());
    }

    @Test
    void rejectsCrossOriginAndWrongContentType() throws Exception {
        assertEquals(403, post(VALID, "https://untrusted.example", "application/x-www-form-urlencoded").statusCode());
        assertEquals(415, post(VALID, base, "application/json").statusCode());
        var request = HttpRequest.newBuilder(URI.create(base + "/feedback"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(VALID)).build();
        assertEquals(403, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void userMarkupIsNeverReflected() throws Exception {
        String markup = URLEncoder.encode("<script>alert(1)</script>", StandardCharsets.UTF_8);
        var response = post(VALID.replace("Clear+and+simple", markup));
        assertEquals(200, response.statusCode());
        assertFalse(response.body().contains("<script>"));
        assertFalse(response.body().contains("alert(1)"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/missing", "/../pom.xml", "/index.html/extra"})
    void onlyExplicitRoutesAreServed(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).GET().build();
        assertEquals(404, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void healthAssetsAndMethodRestrictions() throws Exception {
        for (String path : new String[]{"/health", "/style.css", "/tokens.css"}) {
            var request = HttpRequest.newBuilder(URI.create(base + path)).GET().build();
            assertEquals(200, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
        var request = HttpRequest.newBuilder(URI.create(base + "/feedback")).GET().build();
        assertEquals(405, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void decodingAndMissingDataAreHandled() {
        assertEquals("A & B", FeedbackServer.parseForm("name=A+%26+B").get("name"));
        assertFalse(FeedbackServer.valid(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> FeedbackServer.parseForm("name=%ZZ"));
    }
}
