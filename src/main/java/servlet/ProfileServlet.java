package servlet;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

import com.google.gson.Gson;

import util.DatabaseConnection;
import util.FilterEncodingUTF8;
import util.PasswordUtils;

@WebServlet("/ProfileServlet")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,  // 2MB
    maxFileSize = 1024 * 1024 * 10,       // 10MB
    maxRequestSize = 1024 * 1024 * 50     // 50MB
)
public class ProfileServlet extends HttpServlet {
    private static final Gson gson = new Gson();
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        PrintWriter out = response.getWriter();
        
        String action = request.getParameter("action");
        HttpSession session = request.getSession(false);
        Integer userId = (session != null) ? (Integer) session.getAttribute("user_id") : null;
        
        if (userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            
            switch (action != null ? action : "") {
                case "getProfile":
                case "getProfileData":
                    getProfile(conn, userId, out);
                    break;
                    
                case "getAddresses":
                case "getUserAddresses":
                    getUserAddresses(conn, userId, out);
                    break;
                    
                case "getWalletInfo":
                    getWalletInfo(conn, userId, out);
                    break;
                    
                case "getUserStats":
                    getUserStats(conn, userId, out);
                    break;
                    
                case "getUserByUsername":
                    String username = request.getParameter("username");
                    if (username != null && !username.trim().isEmpty()) {
                        getUserByUsername(conn, username.trim(), out);
                    } else {
                        sendError(response, "ユーザー名が必要です");
                    }
                    break;
                    
                default:
                    sendError(response, "無効なアクションです");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(conn);
        }
    }
    
    // Upload directory for avatars
    private static final String AVATAR_UPLOAD_DIR = "img/avatars";
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json; charset=UTF-8");
        PrintWriter out = response.getWriter();
        
        String action = request.getParameter("action");
        HttpSession session = request.getSession(false);
        Integer userId = (session != null) ? (Integer) session.getAttribute("user_id") : null;
        
        if (userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            boolean success = false;
            
            switch (action != null ? action : "") {
                case "updateProfile":
                    success = updateProfile(conn, userId, request, out);
                    break;
                    
                case "uploadAvatar":
                    success = uploadAvatar(conn, userId, request, out);
                    break;
                    
                case "addAddress":
                    success = addAddress(conn, userId, request, out);
                    break;
                    
                case "updateAddress":
                    success = updateAddress(conn, userId, request, out);
                    break;
                    
                case "deleteAddress":
                    success = deleteAddress(conn, userId, request, out);
                    break;
                    
                case "setDefaultAddress":
                    success = setDefaultAddress(conn, userId, request, out);
                    break;
                    
                case "changePassword":
                    success = changePassword(conn, userId, request, out);
                    break;
                    
                default:
                    sendError(response, "無効なアクションです");
                    return;
            }
            
            if (success) {
                conn.commit();
            } else {
                conn.rollback();
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            sendError(response, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(conn);
        }
    }
    
    // ==================== GET PROFILE DATA ====================
    private void getProfile(Connection conn, int userId, PrintWriter out) throws SQLException {
        String sql = """
            SELECT 
                u.user_id,
                u.username,
                u.email,
                u.full_name,
                u.phone,
                u.avatar_url,
                u.bio,
                u.role,
                u.is_verified,
                u.is_seller,
                u.created_at,
                u.last_login,
                w.balance,
                w.frozen_balance,
                w.total_earned,
                w.total_spent,
                (SELECT COUNT(*) FROM products p WHERE p.user_id = u.user_id) as products_count
            FROM users u
            LEFT JOIN user_wallets w ON u.user_id = w.user_id
            WHERE u.user_id = ?
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> userData = new HashMap<>();
                userData.put("user_id", rs.getInt("user_id"));
                userData.put("username", rs.getString("username"));
                userData.put("email", rs.getString("email"));
                userData.put("full_name", rs.getString("full_name"));
                userData.put("phone", rs.getString("phone"));
                userData.put("avatar_url", rs.getString("avatar_url"));
                userData.put("bio", rs.getString("bio"));
                userData.put("role", rs.getString("role"));
                userData.put("is_verified", rs.getBoolean("is_verified"));
                userData.put("is_seller", rs.getBoolean("is_seller"));
                userData.put("created_at", rs.getTimestamp("created_at"));
                userData.put("last_login", rs.getTimestamp("last_login"));
                userData.put("products_count", rs.getInt("products_count"));
                
                Map<String, Object> wallet = new HashMap<>();
                wallet.put("balance", rs.getBigDecimal("balance"));
                wallet.put("frozen_balance", rs.getBigDecimal("frozen_balance"));
                wallet.put("total_earned", rs.getBigDecimal("total_earned"));
                wallet.put("total_spent", rs.getBigDecimal("total_spent"));
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("user", userData);
                result.put("wallet", wallet);
                
                out.print(gson.toJson(result));
            } else {
                sendError(out, "ユーザーが見つかりません");
            }
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
    }
    
    // Alias for backward compatibility
    private void getProfileData(Connection conn, int userId, PrintWriter out) throws SQLException {
        getProfile(conn, userId, out);
    }
    
    // ==================== GET USER ADDRESSES ====================
    private void getUserAddresses(Connection conn, int userId, PrintWriter out) throws SQLException {
        String sql = """
            SELECT 
                address_id,
                address_label,
                recipient_name,
                phone,
                postal_code,
                prefecture,
                city,
                address_line1,
                address_line2,
                building_name,
                is_default,
                created_at,
                updated_at
            FROM user_addresses
            WHERE user_id = ?
            ORDER BY is_default DESC, created_at DESC
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            
            rs = stmt.executeQuery();
            List<Map<String, Object>> addresses = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> address = new HashMap<>();
                address.put("address_id", rs.getInt("address_id"));
                address.put("address_label", rs.getString("address_label"));
                address.put("recipient_name", rs.getString("recipient_name"));
                address.put("phone", rs.getString("phone"));
                address.put("postal_code", rs.getString("postal_code"));
                address.put("prefecture", rs.getString("prefecture"));
                address.put("city", rs.getString("city"));
                address.put("address_line1", rs.getString("address_line1"));
                address.put("address_line2", rs.getString("address_line2"));
                address.put("building_name", rs.getString("building_name"));
                address.put("is_default", rs.getBoolean("is_default"));
                address.put("created_at", rs.getTimestamp("created_at"));
                address.put("updated_at", rs.getTimestamp("updated_at"));
                
                addresses.add(address);
            }
            
            // Return addresses in consistent format
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("addresses", addresses);
            out.print(gson.toJson(result));
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
    }
    
    // ==================== GET WALLET INFO ====================
    private void getWalletInfo(Connection conn, int userId, PrintWriter out) throws SQLException {
        String sql = """
            SELECT 
                wallet_id,
                balance,
                frozen_balance,
                total_earned,
                total_spent,
                last_transaction_at,
                created_at,
                updated_at
            FROM user_wallets 
            WHERE user_id = ?
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> wallet = new HashMap<>();
                wallet.put("wallet_id", rs.getInt("wallet_id"));
                wallet.put("balance", rs.getBigDecimal("balance"));
                wallet.put("frozen_balance", rs.getBigDecimal("frozen_balance"));
                wallet.put("total_earned", rs.getBigDecimal("total_earned"));
                wallet.put("total_spent", rs.getBigDecimal("total_spent"));
                wallet.put("last_transaction_at", rs.getTimestamp("last_transaction_at"));
                wallet.put("created_at", rs.getTimestamp("created_at"));
                wallet.put("updated_at", rs.getTimestamp("updated_at"));
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("wallet", wallet);
                
                out.print(gson.toJson(result));
            } else {
                // Create wallet if doesn't exist
                createWallet(conn, userId);
                getWalletInfo(conn, userId, out); // Recursive call
            }
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
    }
    
    // ==================== GET USER BY USERNAME ====================
    private void getUserByUsername(Connection conn, String username, PrintWriter out) throws SQLException {
        String sql = """
            SELECT 
                u.user_id,
                u.username,
                u.email,
                u.full_name,
                u.avatar_url,
                u.is_verified,
                u.is_seller,
                u.role
            FROM users u
            WHERE u.username = ?
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);
            
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("user_id", rs.getInt("user_id"));
                user.put("username", rs.getString("username"));
                user.put("email", rs.getString("email"));
                user.put("full_name", rs.getString("full_name"));
                user.put("avatar_url", rs.getString("avatar_url"));
                user.put("is_verified", rs.getBoolean("is_verified"));
                user.put("is_seller", rs.getBoolean("is_seller"));
                user.put("role", rs.getString("role"));
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("user", user);
                
                out.print(gson.toJson(result));
            } else {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "ユーザーが見つかりません");
                out.print(gson.toJson(result));
            }
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
    }
    
    // ==================== GET USER STATS ====================
    private void getUserStats(Connection conn, int userId, PrintWriter out) throws SQLException {
        String sql = """
            SELECT 
                COUNT(DISTINCT p.product_id) as total_products,
                COUNT(DISTINCT o.order_id) as total_orders,
                COUNT(DISTINCT s.order_id) as total_sales,
                COALESCE(SUM(CASE WHEN o.payment_status = 'paid' THEN o.total_amount ELSE 0 END), 0) as total_spent,
                COALESCE(SUM(CASE WHEN s.order_status = 'delivered' THEN s.total_amount ELSE 0 END), 0) as total_earned,
                COUNT(DISTINCT r.review_id) as total_reviews,
                COALESCE(AVG(r.rating), 0) as average_rating
            FROM users u
            LEFT JOIN products p ON u.user_id = p.user_id
            LEFT JOIN orders o ON u.user_id = o.user_id
            LEFT JOIN orders s ON EXISTS (
                SELECT 1 FROM order_items oi 
                WHERE oi.order_id = s.order_id AND oi.seller_id = u.user_id
            )
            LEFT JOIN product_reviews r ON u.user_id = r.user_id AND r.status = 'approved'
            WHERE u.user_id = ?
            GROUP BY u.user_id
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            
            rs = stmt.executeQuery();
            
            Map<String, Object> stats = new HashMap<>();
            if (rs.next()) {
                stats.put("total_products", rs.getInt("total_products"));
                stats.put("products_count", rs.getInt("total_products"));
                stats.put("total_orders", rs.getInt("total_orders"));
                stats.put("total_sales", rs.getInt("total_sales"));
                stats.put("total_spent", rs.getBigDecimal("total_spent"));
                stats.put("total_earned", rs.getBigDecimal("total_earned"));
                stats.put("total_reviews", rs.getInt("total_reviews"));
                stats.put("average_rating", rs.getDouble("average_rating"));
                stats.put("rating", rs.getDouble("average_rating"));
            } else {
                // Default values
                stats.put("total_products", 0);
                stats.put("products_count", 0);
                stats.put("total_orders", 0);
                stats.put("total_sales", 0);
                stats.put("total_spent", 0);
                stats.put("total_earned", 0);
                stats.put("total_reviews", 0);
                stats.put("average_rating", 0);
                stats.put("rating", 0);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("stats", stats);
            
            out.print(gson.toJson(result));
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
    }
    
    // ==================== UPDATE PROFILE ====================
    private boolean updateProfile(Connection conn, int userId, HttpServletRequest request, PrintWriter out) throws SQLException, IOException {
        String fullName = request.getParameter("full_name");
        String phone = request.getParameter("phone");
        String bio = request.getParameter("bio");
        String email = request.getParameter("email");
        
        StringBuilder sql = new StringBuilder("UPDATE users SET ");
        List<Object> params = new ArrayList<>();
        
        if (fullName != null && !fullName.trim().isEmpty()) {
            sql.append("full_name = ?, ");
            params.add(fullName.trim());
        }
        if (phone != null && !phone.trim().isEmpty()) {
            sql.append("phone = ?, ");
            params.add(phone.trim());
        }
        if (bio != null) {
            sql.append("bio = ?, ");
            params.add(bio);
        }
        if (email != null && !email.trim().isEmpty()) {
            sql.append("email = ?, ");
            params.add(email.trim());
        }
        
        sql.append("updated_at = NOW() WHERE user_id = ?");
        params.add(userId);
        
        PreparedStatement stmt = null;
        
        try {
            stmt = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                conn.commit();
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "プロフィールを更新しました");
                
                out.print(gson.toJson(result));
                return true;
            } else {
                conn.rollback();
                sendError(out, "プロフィールの更新に失敗しました");
                return false;
            }
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            DatabaseConnection.closeResources(stmt);
        }
    }
    
    // ==================== UPLOAD AVATAR ====================
    private boolean uploadAvatar(Connection conn, int userId, HttpServletRequest request, PrintWriter out) throws SQLException, IOException {
        try {
            // Get upload path - ALWAYS use source directory for development
            String uploadPath = null;
            String sourcePath = null;
            String deployPath = null;
            
            // Get workspace directory - use CATALINA_BASE to find workspace
            String realPath = getServletContext().getRealPath("/");
            String workspacePath = null;
            
            // Method 1: Use CATALINA_BASE system property
            String catalinaBase = System.getProperty("catalina.base");
            if (catalinaBase != null && !catalinaBase.isEmpty()) {
                // CATALINA_BASE: C:\pleiades\2025-03\workspace\.metadata\.plugins\org.eclipse.wst.server.core\tmp0
                // We need: C:\pleiades\2025-03\workspace\KaruruFleaMarket
                File catalinaBaseFile = new File(catalinaBase);
                File currentDir = catalinaBaseFile;
                
                // Go up until we find "workspace" directory
                int maxDepth = 10;
                int depth = 0;
                while (currentDir != null && depth < maxDepth) {
                    if (currentDir.getName().equals("workspace")) {
                        // Found workspace directory, now look for KaruruFleaMarket inside it
                        File[] children = currentDir.listFiles();
                        if (children != null) {
                            for (File child : children) {
                                if (child.isDirectory() && child.getName().equals("KaruruFleaMarket")) {
                                    // Check if it has src/main/webapp
                                    File srcMainWebapp = new File(child, "src" + File.separator + "main" + File.separator + "webapp");
                                    if (srcMainWebapp.exists() && srcMainWebapp.isDirectory()) {
                                        workspacePath = child.getAbsolutePath();
                                        System.out.println("[AvatarUpload] Found workspace project via CATALINA_BASE: " + workspacePath);
                                        break;
                                    }
                                }
                            }
                        }
                        if (workspacePath != null) break;
                    }
                    currentDir = currentDir.getParentFile();
                    depth++;
                }
            }
            
            // Method 2: Traverse from deployment path
            if (workspacePath == null && realPath != null && !realPath.isEmpty()) {
                File deployDir = new File(realPath);
                File currentDir = deployDir;
                
                // Go up until we find directory with src/main/webapp
                int maxDepth = 15;
                int depth = 0;
                while (currentDir != null && depth < maxDepth) {
                    File srcMainWebapp = new File(currentDir, "src" + File.separator + "main" + File.separator + "webapp");
                    if (srcMainWebapp.exists() && srcMainWebapp.isDirectory()) {
                        workspacePath = currentDir.getAbsolutePath();
                        System.out.println("[AvatarUpload] Found project root by traversing from deployment: " + workspacePath);
                        break;
                    }
                    currentDir = currentDir.getParentFile();
                    depth++;
                }
            }
            
            // If extraction failed, try user.dir but check if it's workspace
            if (workspacePath == null || workspacePath.isEmpty()) {
                String userDir = System.getProperty("user.dir");
                if (userDir != null && userDir.contains("KaruruFleaMarket")) {
                    // user.dir might already be in workspace
                    if (userDir.endsWith("KaruruFleaMarket")) {
                        workspacePath = userDir;
                    } else {
                        // Find KaruruFleaMarket in the path
                        int projectIndex = userDir.indexOf("KaruruFleaMarket");
                        if (projectIndex >= 0) {
                            workspacePath = userDir.substring(0, projectIndex + "KaruruFleaMarket".length());
                        }
                    }
                }
            }
            
            // Final fallback: use user.dir and assume it's workspace root
            if (workspacePath == null || workspacePath.isEmpty()) {
                String userDir = System.getProperty("user.dir");
                workspacePath = userDir;
                System.out.println("[AvatarUpload] Warning: Using user.dir as fallback: " + workspacePath);
            }
            
            // Build absolute path to source directory
            File workspaceDir = new File(workspacePath);
            File sourceDir = new File(workspaceDir, "src" + File.separator + "main" + File.separator + "webapp" + File.separator + AVATAR_UPLOAD_DIR.replace("/", File.separator));
            sourcePath = sourceDir.getAbsolutePath();
            
            System.out.println("[AvatarUpload] Workspace dir: " + workspaceDir.getAbsolutePath());
            System.out.println("[AvatarUpload] Source path (absolute): " + sourcePath);
            
            // Also get deployment path (for copying later)
            realPath = getServletContext().getRealPath("/");
            if (realPath != null && !realPath.isEmpty()) {
                deployPath = realPath + AVATAR_UPLOAD_DIR.replace("/", File.separator);
            } else {
                realPath = getServletContext().getRealPath("");
                if (realPath != null && !realPath.isEmpty()) {
                    deployPath = realPath + File.separator + AVATAR_UPLOAD_DIR.replace("/", File.separator);
                }
            }
            
            // ALWAYS use source directory - never deployment directory
            uploadPath = sourcePath;
            System.out.println("[AvatarUpload] Using SOURCE directory (absolute): " + uploadPath);
            
            if (deployPath != null) {
                System.out.println("[AvatarUpload] Deployment path (for reference): " + deployPath);
                System.out.println("[AvatarUpload] Paths are different: " + !uploadPath.equals(deployPath));
            } else {
                System.out.println("[AvatarUpload] Deployment path not available");
            }
            
            // Create directory if it doesn't exist
            File uploadDir = new File(uploadPath);
            if (!uploadDir.exists()) {
                boolean created = uploadDir.mkdirs();
                System.out.println("[AvatarUpload] Directory created: " + created + " at " + uploadPath);
                if (!created) {
                    sendError(out, "アップロードディレクトリの作成に失敗しました: " + uploadPath);
                    return false;
                }
            }
            
            // Check if directory is writable
            if (!uploadDir.canWrite()) {
                sendError(out, "アップロードディレクトリに書き込み権限がありません: " + uploadPath);
                return false;
            }
            
            Part avatarPart = request.getPart("avatar");
            String avatarUrl = null;
            
            if (avatarPart == null) {
                sendError(out, "アバター画像が見つかりません");
                return false;
            }
            
            System.out.println("[AvatarUpload] Part name: " + avatarPart.getName());
            System.out.println("[AvatarUpload] Part size: " + avatarPart.getSize());
            System.out.println("[AvatarUpload] Content type: " + avatarPart.getContentType());
            
            if (avatarPart.getSize() > 0) {
                String originalFileName = getFileName(avatarPart);
                System.out.println("[AvatarUpload] Original filename: " + originalFileName);
                
                if (originalFileName == null || originalFileName.isEmpty() || 
                    originalFileName.equals("unknown_" + System.currentTimeMillis())) {
                    sendError(out, "無効なファイル名です");
                    return false;
                }
                
                // Validate file type
                String contentType = avatarPart.getContentType();
                if (contentType == null || !contentType.startsWith("image/")) {
                    sendError(out, "画像ファイルのみアップロードできます");
                    return false;
                }
                
                // Validate file size (10MB)
                if (avatarPart.getSize() > 10 * 1024 * 1024) {
                    sendError(out, "ファイルサイズは10MB以下である必要があります");
                    return false;
                }
                
                // Generate unique filename
                String fileExtension = "";
                int lastDot = originalFileName.lastIndexOf('.');
                if (lastDot > 0) {
                    fileExtension = originalFileName.substring(lastDot);
                }
                String fileName = UUID.randomUUID().toString() + fileExtension;
                String filePath = uploadPath + File.separator + fileName;
                
                System.out.println("[AvatarUpload] Saving file: " + fileName);
                System.out.println("[AvatarUpload] Full path: " + filePath);
                
                // Save file
                try {
                    // Ensure parent directory exists
                    File parentDir = new File(uploadPath);
                    if (!parentDir.exists()) {
                        boolean created = parentDir.mkdirs();
                        System.out.println("[AvatarUpload] Parent directory created: " + created);
                    }
                    
                    // Use InputStream to save file manually for better control
                    java.io.InputStream fileContent = avatarPart.getInputStream();
                    System.out.println("[AvatarUpload] InputStream obtained, starting file write...");
                    
                    java.io.FileOutputStream outputStream = new java.io.FileOutputStream(filePath);
                    System.out.println("[AvatarUpload] FileOutputStream created for: " + filePath);
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytesWritten = 0;
                    while ((bytesRead = fileContent.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytesWritten += bytesRead;
                    }
                    
                    outputStream.flush();
                    outputStream.close();
                    fileContent.close();
                    
                    System.out.println("[AvatarUpload] Stream closed. Total bytes written: " + totalBytesWritten);
                    
                    // Verify file was saved
                    File savedFile = new File(filePath);
                    System.out.println("[AvatarUpload] Checking if file exists: " + savedFile.exists());
                    System.out.println("[AvatarUpload] File absolute path: " + savedFile.getAbsolutePath());
                    
                    if (!savedFile.exists()) {
                        System.err.println("[AvatarUpload] ERROR: File was not saved! Path: " + filePath);
                        System.err.println("[AvatarUpload] Absolute path: " + savedFile.getAbsolutePath());
                        sendError(out, "ファイルの保存に失敗しました");
                        return false;
                    }
                    
                    long fileSize = savedFile.length();
                    System.out.println("[AvatarUpload] File exists! Size: " + fileSize + " bytes");
                    
                    if (fileSize == 0) {
                        System.err.println("[AvatarUpload] ERROR: Saved file is empty!");
                        savedFile.delete();
                        sendError(out, "ファイルが空です");
                        return false;
                    }
                    
                    if (fileSize != avatarPart.getSize()) {
                        System.err.println("[AvatarUpload] WARNING: File size mismatch! Expected: " + 
                                         avatarPart.getSize() + ", Got: " + fileSize);
                    }
                    
                    System.out.println("[AvatarUpload] File saved successfully. Size: " + fileSize + " bytes");
                    System.out.println("[AvatarUpload] File can be read: " + savedFile.canRead());
                    System.out.println("[AvatarUpload] File can be written: " + savedFile.canWrite());
                    
                    // Also save to deployment directory if different from source
                    if (deployPath != null && !uploadPath.equals(deployPath)) {
                        try {
                            File deployDir = new File(deployPath);
                            if (!deployDir.exists()) {
                                deployDir.mkdirs();
                            }
                            
                            String deployFilePath = deployPath + File.separator + fileName;
                            java.io.FileInputStream sourceStream = new java.io.FileInputStream(savedFile);
                            java.io.FileOutputStream deployStream = new java.io.FileOutputStream(deployFilePath);
                            
                            byte[] copyBuffer = new byte[8192];
                            int copyBytesRead;
                            while ((copyBytesRead = sourceStream.read(copyBuffer)) != -1) {
                                deployStream.write(copyBuffer, 0, copyBytesRead);
                            }
                            
                            deployStream.close();
                            sourceStream.close();
                            
                            System.out.println("[AvatarUpload] File also copied to deployment directory: " + deployFilePath);
                        } catch (Exception e) {
                            System.err.println("[AvatarUpload] Warning: Failed to copy to deployment directory: " + e.getMessage());
                            // Continue anyway - source file is saved
                        }
                    }
                    
                    // Save to database - use forward slash for URL
                    avatarUrl = AVATAR_UPLOAD_DIR + "/" + fileName;
                    
                    System.out.println("[AvatarUpload] Avatar URL: " + avatarUrl);
                } catch (IOException e) {
                    System.err.println("[AvatarUpload] Error saving file: " + e.getMessage());
                    e.printStackTrace();
                    sendError(out, "ファイルの保存中にエラーが発生しました: " + e.getMessage());
                    return false;
                }
            } else {
                sendError(out, "ファイルサイズが0です");
                return false;
            }
            
            if (avatarUrl == null) {
                sendError(out, "アバター画像を選択してください");
                return false;
            }
            
            // Update user avatar in database
            String sql = "UPDATE users SET avatar_url = ?, updated_at = NOW() WHERE user_id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, avatarUrl);
            stmt.setInt(2, userId);
            
            int affectedRows = stmt.executeUpdate();
            stmt.close();
            
            if (affectedRows > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "アバターを更新しました");
                result.put("avatar_url", avatarUrl);
                
                out.print(gson.toJson(result));
                return true;
            } else {
                sendError(out, "アバターの更新に失敗しました");
                return false;
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(out, "エラー: " + e.getMessage());
            return false;
        }
    }
    
    // ==================== GET FILE NAME FROM PART ====================
    private String getFileName(Part part) {
        String contentDisposition = part.getHeader("content-disposition");
        if (contentDisposition == null) {
            return "unknown_" + System.currentTimeMillis();
        }
        
        String[] tokens = contentDisposition.split(";");
        for (String token : tokens) {
            if (token.trim().startsWith("filename")) {
                String fileName = token.substring(token.indexOf("=") + 2, token.length() - 1);
                if (fileName != null && !fileName.isEmpty()) {
                    return fileName;
                }
            }
        }
        
        return "unknown_" + System.currentTimeMillis();
    }
    
    // ==================== ADD ADDRESS ====================
    private boolean addAddress(Connection conn, int userId, HttpServletRequest request, PrintWriter out) throws SQLException {
        String addressLabel = request.getParameter("address_label");
        String recipientName = request.getParameter("recipient_name");
        String phone = request.getParameter("phone");
        String postalCode = request.getParameter("postal_code");
        String prefecture = request.getParameter("prefecture");
        String city = request.getParameter("city");
        String addressLine1 = request.getParameter("address_line1");
        String addressLine2 = request.getParameter("address_line2");
        String buildingName = request.getParameter("building_name");
        boolean isDefault = "true".equals(request.getParameter("is_default"));
        
        // If setting as default, remove default from other addresses
        if (isDefault) {
            removeDefaultAddress(conn, userId);
        }
        
        String sql = """
            INSERT INTO user_addresses 
            (user_id, address_label, recipient_name, phone, postal_code, 
             prefecture, city, address_line1, address_line2, building_name, country, is_default, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '日本', ?, NOW(), NOW())
            """;
        
        PreparedStatement stmt = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            stmt.setString(2, addressLabel);
            stmt.setString(3, recipientName);
            stmt.setString(4, phone);
            stmt.setString(5, postalCode);
            stmt.setString(6, prefecture);
            stmt.setString(7, city);
            stmt.setString(8, addressLine1);
            stmt.setString(9, addressLine2);
            stmt.setString(10, buildingName);
            stmt.setBoolean(11, isDefault);
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "住所を追加しました");
                
                out.print(gson.toJson(result));
                return true;
            } else {
                sendError(out, "住所の追加に失敗しました");
                return false;
            }
        } finally {
            DatabaseConnection.closeResources(stmt);
        }
    }
    
    // ==================== UPDATE ADDRESS ====================
    private boolean updateAddress(Connection conn, int userId, HttpServletRequest request, PrintWriter out) throws SQLException {
        int addressId = Integer.parseInt(request.getParameter("address_id"));
        String addressLabel = request.getParameter("address_label");
        String recipientName = request.getParameter("recipient_name");
        String phone = request.getParameter("phone");
        String postalCode = request.getParameter("postal_code");
        String prefecture = request.getParameter("prefecture");
        String city = request.getParameter("city");
        String addressLine1 = request.getParameter("address_line1");
        String addressLine2 = request.getParameter("address_line2");
        String buildingName = request.getParameter("building_name");
        boolean isDefault = "true".equals(request.getParameter("is_default"));
        
        // If setting as default, remove default from other addresses
        if (isDefault) {
            removeDefaultAddress(conn, userId);
        }
        
        String sql = """
            UPDATE user_addresses 
            SET address_label = ?, recipient_name = ?, phone = ?, postal_code = ?,
                prefecture = ?, city = ?, address_line1 = ?, address_line2 = ?,
                building_name = ?, is_default = ?, updated_at = NOW()
            WHERE address_id = ? AND user_id = ?
            """;
        
        PreparedStatement stmt = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, addressLabel);
            stmt.setString(2, recipientName);
            stmt.setString(3, phone);
            stmt.setString(4, postalCode);
            stmt.setString(5, prefecture);
            stmt.setString(6, city);
            stmt.setString(7, addressLine1);
            stmt.setString(8, addressLine2);
            stmt.setString(9, buildingName);
            stmt.setBoolean(10, isDefault);
            stmt.setInt(11, addressId);
            stmt.setInt(12, userId);
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "住所を更新しました");
                
                out.print(gson.toJson(result));
                return true;
            } else {
                sendError(out, "住所の更新に失敗しました");
                return false;
            }
        } finally {
            DatabaseConnection.closeResources(stmt);
        }
    }
    
    // ==================== DELETE ADDRESS ====================
    private boolean deleteAddress(Connection conn, int userId, HttpServletRequest request, PrintWriter out) throws SQLException {
        int addressId = Integer.parseInt(request.getParameter("address_id"));
        
        // Check if this is the default address
        boolean isDefault = isDefaultAddress(conn, addressId);
        
        String sql = "DELETE FROM user_addresses WHERE address_id = ? AND user_id = ?";
        
        PreparedStatement stmt = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, addressId);
            stmt.setInt(2, userId);
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                // If deleted address was default, set another address as default
                if (isDefault) {
                    setFirstAddressAsDefault(conn, userId);
                }
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "住所を削除しました");
                
                out.print(gson.toJson(result));
                return true;
            } else {
                sendError(out, "住所の削除に失敗しました");
                return false;
            }
        } finally {
            DatabaseConnection.closeResources(stmt);
        }
    }
    
    // ==================== SET DEFAULT ADDRESS ====================
    private boolean setDefaultAddress(Connection conn, int userId, HttpServletRequest request, PrintWriter out) throws SQLException {
        int addressId = Integer.parseInt(request.getParameter("address_id"));
        
        // Remove default from all addresses
        removeDefaultAddress(conn, userId);
        
        // Set new default
        String sql = "UPDATE user_addresses SET is_default = 1, updated_at = NOW() WHERE address_id = ? AND user_id = ?";
        
        PreparedStatement stmt = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, addressId);
            stmt.setInt(2, userId);
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "デフォルト住所を設定しました");
                
                out.print(gson.toJson(result));
                return true;
            } else {
                sendError(out, "デフォルト住所の設定に失敗しました");
                return false;
            }
        } finally {
            DatabaseConnection.closeResources(stmt);
        }
    }
    
    // ==================== CHANGE PASSWORD ====================
    private boolean changePassword(Connection conn, int userId, HttpServletRequest request, PrintWriter out) throws SQLException {
        String currentPassword = request.getParameter("current_password");
        String newPassword = request.getParameter("new_password");
        
        if (currentPassword == null || newPassword == null || 
            currentPassword.trim().isEmpty() || newPassword.trim().isEmpty()) {
            sendError(out, "現在のパスワードと新しいパスワードが必要です");
            return false;
        }
        
        // Validate new password strength
        if (!PasswordUtils.isValidPassword(newPassword)) {
            sendError(out, "新しいパスワードは6文字以上128文字以下である必要があります");
            return false;
        }
        
        // Get current password hash
        String checkSql = "SELECT password_hash FROM users WHERE user_id = ?";
        PreparedStatement checkStmt = null;
        ResultSet rs = null;
        
        try {
            checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, userId);
            rs = checkStmt.executeQuery();
            
            if (!rs.next()) {
                sendError(out, "ユーザーが見つかりません");
                return false;
            }
            
            String storedHash = rs.getString("password_hash");
            
            // Verify current password using BCrypt
            if (!PasswordUtils.checkPassword(currentPassword, storedHash)) {
                sendError(out, "現在のパスワードが正しくありません");
                return false;
            }
            
            // Hash new password with BCrypt
            String newPasswordHash = PasswordUtils.hashPassword(newPassword);
            
            // Update password
            String updateSql = "UPDATE users SET password_hash = ?, updated_at = NOW() WHERE user_id = ?";
            PreparedStatement updateStmt = null;
            
            try {
                updateStmt = conn.prepareStatement(updateSql);
                updateStmt.setString(1, newPasswordHash);
                updateStmt.setInt(2, userId);
                
                int affectedRows = updateStmt.executeUpdate();
                
                if (affectedRows > 0) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("message", "パスワードを変更しました");
                    
                    out.print(gson.toJson(result));
                    return true;
                } else {
                    sendError(out, "パスワードの変更に失敗しました");
                    return false;
                }
            } finally {
                DatabaseConnection.closeResources(updateStmt);
            }
            
        } catch (IllegalArgumentException e) {
            sendError(out, e.getMessage());
            return false;
        } catch (Exception e) {
            sendError(out, "パスワードの変更中にエラーが発生しました");
            e.printStackTrace();
            return false;
        } finally {
            DatabaseConnection.closeResources(rs, checkStmt);
        }
    }
    
    // ==================== HELPER METHODS ====================
    private void createWallet(Connection conn, int userId) throws SQLException {
        String sql = "INSERT INTO user_wallets (user_id, balance, frozen_balance, total_earned, total_spent) VALUES (?, 0, 0, 0, 0)";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
    }
    
    private void removeDefaultAddress(Connection conn, int userId) throws SQLException {
        String sql = "UPDATE user_addresses SET is_default = 0 WHERE user_id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
    }
    
    private boolean isDefaultAddress(Connection conn, int addressId) throws SQLException {
        String sql = "SELECT is_default FROM user_addresses WHERE address_id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, addressId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean("is_default");
            }
        }
    }
    
    private void setFirstAddressAsDefault(Connection conn, int userId) throws SQLException {
        String sql = "UPDATE user_addresses SET is_default = 1 WHERE address_id = (SELECT MIN(address_id) FROM user_addresses WHERE user_id = ?)";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
    }
    
    // ==================== UTILITY METHODS ====================
    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        response.getWriter().print(gson.toJson(error));
    }
    
    private void sendError(PrintWriter out, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        out.print(gson.toJson(error));
    }
}