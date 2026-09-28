package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import util.Auth;
import util.DatabaseConnection;
import util.Json;

/**
 * Base for the JSON API servlets, which all follow the same shape: {@code ?action=name} picks a handler per
 * HTTP method. Subclasses register routes in their constructor; this class does the action lookup, the
 * login/admin check, the database connection and the error response when a handler throws SQLException.
 *
 * <pre>
 * public FooServlet() {
 *     route("GET", "list", Access.PUBLIC, this::list);
 *     route("POST", "save", Access.USER, this::save);
 * }
 * </pre>
 */
public abstract class ApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ApiServlet.class.getName());

    protected enum Access { PUBLIC, USER, ADMIN }

    @FunctionalInterface
    protected interface Handler {
        void handle(Call call) throws IOException, SQLException;
    }

    private record Route(Access access, Handler handler) {
    }

    private final Map<String, Route> routes = new HashMap<>();

    /** Registers a handler. Use {@code action = null} for requests without an action parameter. */
    protected final void route(String method, String action, Access access, Handler handler) {
        routes.put(method + " " + (action == null ? "" : action), new Route(access, handler));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatch("GET", request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatch("POST", request, response);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatch("PUT", request, response);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatch("DELETE", request, response);
    }

    private void dispatch(String method, HttpServletRequest request, HttpServletResponse response) throws IOException {
        JsonObject body;
        try {
            body = isJson(request) ? Json.readBody(request) : new JsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            Json.error(response, SC_BAD_REQUEST, "Invalid JSON body");
            return;
        }
        String action = Call.read(request, body, "action");
        Route route = routes.get(method + " " + (action == null ? "" : action));
        if (route == null) {
            Json.error(response, SC_BAD_REQUEST, action == null ? "Action parameter is required" : "Invalid action: " + action);
            return;
        }
        Integer userId = Auth.userId(request);
        if (route.access() != Access.PUBLIC && userId == null) {
            Json.error(response, SC_UNAUTHORIZED, "ログインが必要です");
            return;
        }
        if (route.access() == Access.ADMIN && !Auth.isAdmin(request)) {
            Json.error(response, SC_FORBIDDEN, "管理者権限が必要です");
            return;
        }
        try (Call call = new Call(request, response, body, userId)) {
            route.handler().handle(call);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, getClass().getSimpleName() + " " + method + " " + action + " failed", e);
            if (!response.isCommitted()) {
                Json.error(response, SC_INTERNAL_SERVER_ERROR, "Database error");
            }
        }
    }

    private static boolean isJson(HttpServletRequest request) {
        String type = request.getContentType();
        return type != null && type.toLowerCase().startsWith("application/json");
    }

    /**
     * One API request: request/response, the logged-in user (if any) and a lazily opened DB connection.
     * Parameters are read the same way whether the client sent a form, a query string or a JSON object body.
     */
    protected static final class Call implements AutoCloseable {
        public final HttpServletRequest request;
        public final HttpServletResponse response;
        private final JsonObject body;
        private final Integer userId;
        private Connection conn;

        private Call(HttpServletRequest request, HttpServletResponse response, JsonObject body, Integer userId) {
            this.request = request;
            this.response = response;
            this.body = body;
            this.userId = userId;
        }

        /** Form/query parameter first, then a scalar field of the JSON body; trimmed, null when missing or blank. */
        private static String read(HttpServletRequest request, JsonObject body, String name) {
            String value = request.getParameter(name);
            if (value == null) {
                JsonElement field = body.get(name);
                if (field != null && field.isJsonPrimitive()) {
                    value = field.getAsString();
                }
            }
            return value == null || value.isBlank() ? null : value.trim();
        }

        /** The logged-in user's id. Only call from USER/ADMIN routes, where it is guaranteed to be set. */
        public int userId() {
            return userId;
        }

        /** The logged-in user's id, or null for anonymous callers of PUBLIC routes. */
        public Integer optUserId() {
            return userId;
        }

        public Connection db() throws SQLException {
            if (conn == null) {
                conn = DatabaseConnection.getConnection();
            }
            return conn;
        }

        /** Starts a transaction on {@link #db()}. Anything not committed is rolled back when the request ends. */
        public Connection beginTransaction() throws SQLException {
            Connection db = db();
            db.setAutoCommit(false);
            return db;
        }

        public String param(String name) {
            return read(request, body, name);
        }

        /** Like {@link #param(String)} but never null, so it is safe as a {@code Map.of(...)} key. */
        public String param(String name, String fallback) {
            String value = param(name);
            return value == null ? fallback : value;
        }

        public Integer intParam(String name) {
            String value = param(name);
            if (value == null) {
                return null;
            }
            try {
                return Integer.valueOf(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /** The JSON request body (empty when the request was not JSON). */
        public JsonObject body() {
            return body;
        }

        public void ok(Object body) throws IOException {
            Json.ok(response, body);
        }

        public void error(int status, String message) throws IOException {
            Json.error(response, status, message);
        }

        @Override
        public void close() throws SQLException {
            if (conn == null) {
                return;
            }
            try {
                if (!conn.getAutoCommit()) {
                    conn.rollback();
                }
            } finally {
                conn.close();
            }
        }
    }
}
