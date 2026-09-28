package util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CsrfFilterTest {

    @Test
    void acceptsSameOrigin() {
        assertTrue(CsrfFilter.isSameOrigin("http://localhost:8085", null, "localhost:8085"));
        assertTrue(CsrfFilter.isSameOrigin("https://karuru.example", null, "karuru.example"));
    }

    @Test
    void fallsBackToReferer() {
        assertTrue(CsrfFilter.isSameOrigin(null, "http://localhost:8085/KaruruFleaMarket/cart.jsp", "localhost:8085"));
    }

    @Test
    void rejectsCrossSiteAndMissingHeaders() {
        assertFalse(CsrfFilter.isSameOrigin("http://evil.example", null, "localhost:8085"));
        assertFalse(CsrfFilter.isSameOrigin("http://localhost:9999", null, "localhost:8085"));
        assertFalse(CsrfFilter.isSameOrigin("http://localhost:8085.evil.example", null, "localhost:8085"));
        assertFalse(CsrfFilter.isSameOrigin("null", null, "localhost:8085"));
        assertFalse(CsrfFilter.isSameOrigin(null, null, "localhost:8085"));
    }
}
