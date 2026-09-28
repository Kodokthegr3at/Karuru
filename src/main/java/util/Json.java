package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** JSON request/response helpers shared by all API servlets. */
public final class Json {

    /** Dates go out as ISO-8601 local time ("2026-02-01T10:00:00"), which JavaScript's Date parses. */
    public static final Gson GSON = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create();

    private Json() {
    }

    public static void send(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.getWriter().print(GSON.toJson(body));
    }

    public static void ok(HttpServletResponse response, Object body) throws IOException {
        send(response, HttpServletResponse.SC_OK, body);
    }

    /** Writes {@code {"success": false, "error": message}}. */
    public static void error(HttpServletResponse response, int status, String message) throws IOException {
        send(response, status, obj("success", false, "error", message));
    }

    /** Builds an insertion-ordered map from alternating keys and values: {@code obj("a", 1, "b", 2)}. */
    public static Map<String, Object> obj(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    /** Parses the request body as a JSON object; returns an empty object for an empty body. */
    public static JsonObject readBody(HttpServletRequest request) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        if (body.toString().isBlank()) {
            return new JsonObject();
        }
        return JsonParser.parseString(body.toString()).getAsJsonObject();
    }
}
