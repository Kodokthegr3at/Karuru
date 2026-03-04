<%@ page contentType="application/javascript; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true"%>
<%@ page import="java.io.*" %>
<%
InputStream in = application.getResourceAsStream("/js/dashboard.js");
if (in != null) {
    try (InputStreamReader isr = new InputStreamReader(in, "UTF-8");
         BufferedReader reader = new BufferedReader(isr)) {
        String line;
        while ((line = reader.readLine()) != null) {
            out.print(line);
            out.print("\n");
        }
    }
}
%>
