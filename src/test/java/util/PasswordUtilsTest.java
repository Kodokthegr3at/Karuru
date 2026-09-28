package util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordUtilsTest {

    @Test
    void hashThenCheckRoundTrips() {
        String hash = PasswordUtils.hashPassword("correct horse");
        assertTrue(PasswordUtils.isValidHash(hash));
        assertTrue(PasswordUtils.checkPassword("correct horse", hash));
        assertFalse(PasswordUtils.checkPassword("wrong horse", hash));
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> PasswordUtils.hashPassword("short"));
        assertFalse(PasswordUtils.checkPassword("anything", "not-a-bcrypt-hash"));
        assertFalse(PasswordUtils.checkPassword(null, null));
    }
}
