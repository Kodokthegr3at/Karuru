package util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UploadsTest {

    @TempDir
    Path root;

    @BeforeEach
    void useTempRoot() throws IOException {
        System.setProperty("karuru.upload.dir", root.toString());
        Files.createDirectories(root.resolve("attachments"));
        Files.write(root.resolve("attachments/abc-123.png"), new byte[] {1});
        Files.write(root.resolve("secret.png"), new byte[] {1});
    }

    @AfterEach
    void reset() {
        System.clearProperty("karuru.upload.dir");
    }

    @Test
    void detectsImagesByContentNotName() {
        assertEquals(Uploads.ImageType.JPEG, Uploads.ImageType.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0)));
        assertEquals(Uploads.ImageType.PNG, Uploads.ImageType.detect(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A)));
        assertEquals(Uploads.ImageType.GIF, Uploads.ImageType.detect(bytes('G', 'I', 'F', '8', '9', 'a')));
        assertEquals(Uploads.ImageType.WEBP, Uploads.ImageType.detect(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P')));
        assertNull(Uploads.ImageType.detect("<% out.println(1); %>".getBytes()));
        assertNull(Uploads.ImageType.detect(new byte[0]));
    }

    @Test
    void resolvesOnlyStoredImagesInsideTheUploadDirectory() {
        assertNotNull(Uploads.resolve("/attachments/abc-123.png"));
        assertNull(Uploads.resolve("/attachments/missing.png"));
        assertNull(Uploads.resolve("/../secret.png"));
        assertNull(Uploads.resolve("/attachments/../secret.png"));
        assertNull(Uploads.resolve("/attachments/abc-123.jsp"));
        assertNull(Uploads.resolve("/secret.png"));
        assertNull(Uploads.resolve(null));
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }
}
