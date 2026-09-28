package util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Decodes every request as UTF-8 and defaults responses to UTF-8, before anything reads a parameter.
 * Without it Tomcat falls back to ISO-8859-1 and Japanese form input (messages, listings, sign-up) is
 * stored garbled. Done in code rather than in container config: META-INF/context.xml is ignored when the
 * context is defined elsewhere, and Eclipse WTP cannot load web.xml's &lt;request-character-encoding&gt;.
 *
 * Must be the first filter in web.xml.
 */
public class EncodingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request.getCharacterEncoding() == null) {
            request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        }
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        chain.doFilter(request, response);
    }
}
