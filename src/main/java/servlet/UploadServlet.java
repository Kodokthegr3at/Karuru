package servlet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import util.Uploads;

/** Serves user-uploaded images from {@link Uploads#root()} at /uploads/{category}/{file}. */
@WebServlet("/uploads/*")
public class UploadServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final long CACHE_SECONDS = Duration.ofDays(30).toSeconds();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Path file = Uploads.resolve(request.getPathInfo());
        if (file == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String name = file.getFileName().toString();
        Uploads.ImageType type = Uploads.ImageType.fromExtension(name.substring(name.lastIndexOf('.') + 1));
        response.setContentType(type.contentType);
        response.setHeader("X-Content-Type-Options", "nosniff");
        // File names are random and never reused, so the content never changes.
        response.setHeader("Cache-Control", "public, max-age=" + CACHE_SECONDS + ", immutable");
        response.setContentLengthLong(Files.size(file));
        Files.copy(file, response.getOutputStream());
    }
}
