package util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/** Reads the logged-in user from the HTTP session (set by LoginServlet). */
public final class Auth {

    private Auth() {
    }

    /** The logged-in user's id, or null when not logged in. */
    public static Integer userId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute("user_id");
    }

    public static boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && "admin".equals(session.getAttribute("role"));
    }
}
