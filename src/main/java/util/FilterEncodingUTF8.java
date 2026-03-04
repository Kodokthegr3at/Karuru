package util;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * UTF-8エンコーディングフィルター
 * すべてのリクエストとレスポンスにUTF-8エンコーディングを適用します
 * 
 * Note: Filter ini dikonfigurasi di web.xml, bukan menggunakan @WebFilter annotation
 * untuk menghindari duplikasi konfigurasi.
 */
public class FilterEncodingUTF8 implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 初期化処理（必要に応じて）
        System.out.println("FilterEncodingUTF8 initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        // リクエストのエンコーディング設定
        request.setCharacterEncoding("UTF-8");
        
        // レスポンスのエンコーディング設定
        response.setCharacterEncoding("UTF-8");
        
        // HTTPリクエストの場合、URIに基づいて適切なContent-Typeを設定
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            
            String uri = httpRequest.getRequestURI();
            
            // Content-Typeが未設定の場合のみ、ファイル拡張子に基づいて設定
            if (httpResponse.getContentType() == null) {
                if (uri.endsWith(".css")) {
                    httpResponse.setContentType("text/css; charset=UTF-8");
                } else if (uri.endsWith(".js")) {
                    httpResponse.setContentType("application/javascript; charset=UTF-8");
                } else if (uri.endsWith(".json")) {
                    httpResponse.setContentType("application/json; charset=UTF-8");
                } else if (uri.endsWith(".xml")) {
                    httpResponse.setContentType("application/xml; charset=UTF-8");
                } else if (uri.endsWith(".txt")) {
                    httpResponse.setContentType("text/plain; charset=UTF-8");
                } else if (uri.endsWith(".png")) {
                    httpResponse.setContentType("image/png");
                } else if (uri.endsWith(".jpg") || uri.endsWith(".jpeg")) {
                    httpResponse.setContentType("image/jpeg");
                } else if (uri.endsWith(".gif")) {
                    httpResponse.setContentType("image/gif");
                } else if (uri.endsWith(".svg")) {
                    httpResponse.setContentType("image/svg+xml; charset=UTF-8");
                } else if (uri.endsWith(".ico")) {
                    httpResponse.setContentType("image/x-icon");
                } else if (uri.endsWith(".woff") || uri.endsWith(".woff2")) {
                    httpResponse.setContentType("font/woff");
                } else if (uri.endsWith(".ttf")) {
                    httpResponse.setContentType("font/ttf");
                } else if (uri.endsWith(".pdf")) {
                    httpResponse.setContentType("application/pdf");
                }
                // HTMLやサーブレットの場合はContent-Typeを設定しない
                // サーブレット自身が設定する
            }
        }
        
        // 次のフィルターまたはサーブレットへ処理を渡す
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // クリーンアップ処理（必要に応じて）
        System.out.println("FilterEncodingUTF8 destroyed");
    }
    
    /**
     * 個別のサーブレットで使用するためのユーティリティメソッド
     * JSONレスポンス用のUTF-8設定
     */
    public static void configureUTF8ForJSON(HttpServletRequest request, HttpServletResponse response) {
        try {
            // Set request encoding (harus dilakukan sebelum membaca parameter)
            if (request != null) {
                try {
                    // Hanya set encoding jika belum di-set dan request belum di-parse
                    String encoding = request.getCharacterEncoding();
                    if (encoding == null || encoding.isEmpty()) {
                        request.setCharacterEncoding("UTF-8");
                    }
                } catch (IllegalStateException e) {
                    // Request sudah di-parse, skip encoding setting
                    // Ini normal jika filter sudah mengatur encoding sebelumnya
                } catch (Exception e) {
                    // Catch any other exception (termasuk UnsupportedEncodingException jika terjadi)
                    // UTF-8 seharusnya selalu didukung, tapi kita handle untuk safety
                    System.err.println("Warning: Failed to set request encoding: " + e.getMessage());
                }
            }
            
            // Set response encoding and content type
            if (response != null) {
                try {
                    // Cek apakah response sudah committed
                    if (!response.isCommitted()) {
                        // Set character encoding terlebih dahulu
                        response.setCharacterEncoding("UTF-8");
                        
                        // Set content type (jika belum di-set)
                        String contentType = response.getContentType();
                        if (contentType == null || !contentType.contains("application/json")) {
                            response.setContentType("application/json; charset=UTF-8");
                        }
                        
                        // Set headers untuk cache control
                        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                        response.setHeader("Pragma", "no-cache");
                        response.setDateHeader("Expires", 0);
                    } else {
                        // Response sudah committed, hanya log warning
                        System.err.println("Warning: Response already committed, cannot set UTF-8 encoding");
                    }
                } catch (IllegalStateException e) {
                    // Response sudah committed atau header sudah di-set
                    // Ini bisa terjadi jika filter atau servlet lain sudah mengatur response
                    System.err.println("Warning: Cannot modify response headers: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            // Log error but don't throw - encoding is best effort
            // Jangan biarkan error encoding menghentikan request processing
            System.err.println("Warning: Failed to configure UTF-8 encoding: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * HTML用のUTF-8設定
     */
    public static void configureUTF8ForHTML(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=UTF-8");
    }
    
    /**
     * プレーンテキスト用のUTF-8設定
     */
    public static void configureUTF8ForText(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/plain; charset=UTF-8");
    }
}