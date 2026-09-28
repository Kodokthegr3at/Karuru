package util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

import javax.servlet.http.Part;

/**
 * User-uploaded images. Files live outside the web application (so they survive redeploys and can never be
 * executed as JSP) and are served by {@link servlet.UploadServlet} under /uploads/.
 *
 * Location: system property {@code karuru.upload.dir}, default {@code ${catalina.base}/karuru-uploads}.
 */
public final class Uploads {

    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    /** Supported image formats, detected from the file's first bytes (never from the client's name or header). */
    public enum ImageType {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        GIF("gif", "image/gif"),
        WEBP("webp", "image/webp");

        public final String extension;
        public final String contentType;

        ImageType(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        public static ImageType fromExtension(String extension) {
            for (ImageType type : values()) {
                if (type.extension.equals(extension)) {
                    return type;
                }
            }
            return null;
        }

        static ImageType detect(byte[] head) {
            if (startsWith(head, 0xFF, 0xD8, 0xFF)) {
                return JPEG;
            }
            if (startsWith(head, 0x89, 'P', 'N', 'G')) {
                return PNG;
            }
            if (startsWith(head, 'G', 'I', 'F', '8')) {
                return GIF;
            }
            if (startsWith(head, 'R', 'I', 'F', 'F') && head.length >= 12
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
                return WEBP;
            }
            return null;
        }

        private static boolean startsWith(byte[] data, int... prefix) {
            if (data.length < prefix.length) {
                return false;
            }
            for (int i = 0; i < prefix.length; i++) {
                if ((data[i] & 0xFF) != prefix[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Thrown when an upload is not an acceptable image; the message is safe to show to the user. */
    public static class RejectedUpload extends Exception {
        private static final long serialVersionUID = 1L;

        public RejectedUpload(String message) {
            super(message);
        }
    }

    private Uploads() {
    }

    public static Path root() {
        String configured = System.getProperty("karuru.upload.dir");
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured);
        }
        return Paths.get(System.getProperty("catalina.base", System.getProperty("java.io.tmpdir")), "karuru-uploads");
    }

    /**
     * Stores an uploaded image under {@code category} (e.g. "products") with a random name.
     *
     * @return the URL path relative to the context root, e.g. {@code uploads/products/3f2a….jpg}
     */
    public static String saveImage(Part part, String category) throws IOException, RejectedUpload {
        if (part.getSize() > MAX_IMAGE_BYTES) {
            throw new RejectedUpload("画像サイズは10MB以下にしてください");
        }
        byte[] head;
        try (InputStream in = part.getInputStream()) {
            head = in.readNBytes(12);
        }
        ImageType type = ImageType.detect(head);
        if (type == null) {
            throw new RejectedUpload("JPEG、PNG、GIF、WebP形式の画像のみアップロードできます");
        }
        Path dir = root().resolve(category);
        Files.createDirectories(dir);
        String fileName = UUID.randomUUID() + "." + type.extension;
        try (InputStream in = part.getInputStream()) {
            Files.copy(in, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        }
        return "uploads/" + category + "/" + fileName;
    }

    /**
     * Resolves a request path below /uploads/ to a stored file, or null if the path is malformed,
     * escapes the upload directory, or is not a known image type.
     */
    public static Path resolve(String pathInfo) {
        if (pathInfo == null) {
            return null;
        }
        String[] segments = pathInfo.split("/");
        boolean safe = segments.length == 3 && segments[0].isEmpty()
                && Arrays.stream(segments).skip(1).allMatch(s -> s.matches("[A-Za-z0-9_-]+(\\.[a-z]+)?"));
        if (!safe) {
            return null;
        }
        String name = segments[2];
        int dot = name.lastIndexOf('.');
        if (dot < 0 || ImageType.fromExtension(name.substring(dot + 1)) == null) {
            return null;
        }
        Path root = root().toAbsolutePath().normalize();
        Path file = root.resolve(segments[1]).resolve(name).normalize();
        return file.startsWith(root) && Files.isRegularFile(file) ? file : null;
    }
}
