package servlet;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
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
import com.google.gson.GsonBuilder;

import util.DatabaseConnection;

/**
 * Servlet for Creating Product Listings
 */
@WebServlet("/CreateListingServlet")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,  // 2MB
    maxFileSize = 1024 * 1024 * 10,       // 10MB
    maxRequestSize = 1024 * 1024 * 50     // 50MB
)
public class CreateListingServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    
    // UTF-8対応のGsonインスタンス
    private Gson gson = new GsonBuilder()
        .disableHtmlEscaping()
        .create();
    
    // Upload directories relative to web app
    private static final String UPLOAD_DIR_PRIMARY = "img/products";  // First image
    private static final String UPLOAD_DIR_OTHER = "images";  // Other images
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // UTF-8設定を適用 (multipart/form-data用)
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        
        String action = request.getParameter("action");
        
        try {
            if ("create".equals(action)) {
                createListing(request, response);
            } else if ("update".equals(action)) {
                updateListing(request, response);
            } else {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "無効なアクション");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                     "サーバーエラー: " + e.getMessage());
        }
    }
    
    /**
     * Create new product listing
     */
    private void createListing(HttpServletRequest request, HttpServletResponse response) 
            throws IOException, ServletException {
        
        HttpSession session = request.getSession(false);
        
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "ログインが必要です");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        
        Connection conn = null;
        PreparedStatement stmt = null;
        PreparedStatement setUtf8 = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // UTF-8設定を接続に適用
            setUtf8 = conn.prepareStatement("SET NAMES utf8mb4");
            setUtf8.execute();
            setUtf8.close();
            
            conn.setAutoCommit(false); // Start transaction
            
            // Check if user has address or complete profile data (REQUIRED for creating listing)
            String checkProfileSql = """
                SELECT u.user_id, u.full_name, u.phone, u.email,
                       COUNT(a.address_id) as address_count
                FROM users u
                LEFT JOIN user_addresses a ON u.user_id = a.user_id
                WHERE u.user_id = ?
                GROUP BY u.user_id
            """;
            stmt = conn.prepareStatement(checkProfileSql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            boolean canCreateListing = false;
            if (rs.next()) {
                String fullName = rs.getString("full_name");
                String phone = rs.getString("phone");
                String email = rs.getString("email");
                int addressCount = rs.getInt("address_count");
                
                // User can create listing if:
                // 1. Has at least one address, OR
                // 2. Has complete profile data (full_name, phone, email)
                canCreateListing = (addressCount > 0) || 
                                  (fullName != null && !fullName.trim().isEmpty() &&
                                   phone != null && !phone.trim().isEmpty() &&
                                   email != null && !email.trim().isEmpty());
            }
            rs.close();
            stmt.close();
            
            if (!canCreateListing) {
                conn.rollback();
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, 
                    "商品を出品するには、住所を登録するか、プロフィール情報（氏名・電話番号・メールアドレス）を完成させる必要があります");
                return;
            }
            
            // Get form parameters with UTF-8 support
            String productName = getParameterSafe(request, "product_name");
            String categoryIdStr = request.getParameter("category_id");
            String description = getParameterSafe(request, "description");
            String condition = request.getParameter("condition");
            String brand = getParameterSafe(request, "brand");
            String priceStr = request.getParameter("price");
            String stockStr = request.getParameter("stock_quantity");
            String isRentalStr = request.getParameter("is_rental");
            String rentalPriceDailyStr = request.getParameter("rental_price_daily");
            String rentalPriceWeeklyStr = request.getParameter("rental_price_weekly");
            String rentalPriceMonthlyStr = request.getParameter("rental_price_monthly");
            String isNegotiableStr = request.getParameter("is_negotiable");
            // Fallback to rental_price if rental_price_daily not found (backward compatibility)
            if ((rentalPriceDailyStr == null || rentalPriceDailyStr.isEmpty()) && 
                request.getParameter("rental_price") != null) {
                rentalPriceDailyStr = request.getParameter("rental_price");
            }
            
            // Debug log
            System.out.println("CreateListing - Product Name: " + productName);
            System.out.println("CreateListing - Description: " + description);
            System.out.println("CreateListing - Brand: " + brand);
            
            // Validate required fields
            if (productName == null || productName.trim().isEmpty() ||
                categoryIdStr == null || description == null || 
                condition == null || priceStr == null || stockStr == null) {
                conn.rollback();
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "必須フィールドが不足しています");
                return;
            }
            
            int categoryId = Integer.parseInt(categoryIdStr);
            double price = Double.parseDouble(priceStr);
            int stock = Integer.parseInt(stockStr);
            boolean isRental = "1".equals(isRentalStr) || "true".equals(isRentalStr);
            
            // Generate slug
            String slug = generateSlug(productName);
            
            // Get weight and weight_unit
            String weightStr = request.getParameter("weight");
            String weightUnit = request.getParameter("weight_unit");
            Double weight = null;
            if (weightStr != null && !weightStr.trim().isEmpty()) {
                try {
                    weight = Double.parseDouble(weightStr);
                    // Convert to grams if unit is kg
                    if ("kg".equals(weightUnit)) {
                        weight = weight * 1000; // Convert kg to grams
                    }
                } catch (NumberFormatException e) {
                    // Invalid weight, ignore
                }
            }
            
            // Parse is_negotiable
            boolean isNegotiable = "1".equals(isNegotiableStr) || "true".equals(isNegotiableStr);
            
            // Insert product
            String productSql = "INSERT INTO products " +
                    "(user_id, product_name, slug, description, price, stock_quantity, " +
                    "`condition`, is_rental, rental_price_daily, rental_price_weekly, rental_price_monthly, " +
                    "weight, is_negotiable, status, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'available', NOW())";
            
            stmt = conn.prepareStatement(productSql, Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, userId);
            stmt.setString(2, productName);
            stmt.setString(3, slug);
            stmt.setString(4, description);
            stmt.setDouble(5, price);
            stmt.setInt(6, stock);
            stmt.setString(7, mapCondition(condition));
            stmt.setBoolean(8, isRental);
            
            // Set rental prices
            if (isRental && rentalPriceDailyStr != null && !rentalPriceDailyStr.isEmpty()) {
                stmt.setDouble(9, Double.parseDouble(rentalPriceDailyStr));
            } else {
                stmt.setNull(9, java.sql.Types.DOUBLE);
            }
            
            if (isRental && rentalPriceWeeklyStr != null && !rentalPriceWeeklyStr.isEmpty()) {
                stmt.setDouble(10, Double.parseDouble(rentalPriceWeeklyStr));
            } else {
                stmt.setNull(10, java.sql.Types.DOUBLE);
            }
            
            if (isRental && rentalPriceMonthlyStr != null && !rentalPriceMonthlyStr.isEmpty()) {
                stmt.setDouble(11, Double.parseDouble(rentalPriceMonthlyStr));
            } else {
                stmt.setNull(11, java.sql.Types.DOUBLE);
            }
            
            // Set weight
            if (weight != null) {
                stmt.setDouble(12, weight);
            } else {
                stmt.setNull(12, java.sql.Types.DOUBLE);
            }
            
            // Set is_negotiable
            stmt.setBoolean(13, isNegotiable);
            
            int rowsInserted = stmt.executeUpdate();
            
            if (rowsInserted == 0) {
                conn.rollback();
                sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                         "商品の作成に失敗しました");
                return;
            }
            
            // Get generated product ID
            rs = stmt.getGeneratedKeys();
            int productId = 0;
            if (rs.next()) {
                productId = rs.getInt(1);
            }
            
            rs.close();
            stmt.close();
            
            // Insert category relationship
            String categorySql = "INSERT INTO product_categories (product_id, category_id) VALUES (?, ?)";
            stmt = conn.prepareStatement(categorySql);
            stmt.setInt(1, productId);
            stmt.setInt(2, categoryId);
            stmt.executeUpdate();
            stmt.close();
            
            // Handle image uploads - ALWAYS use source directory for development
            String sourcePath = null;
            
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
                                        System.out.println("[CreateListing] Found workspace project via CATALINA_BASE: " + workspacePath);
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
                        System.out.println("[CreateListing] Found project root by traversing from deployment: " + workspacePath);
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
                System.out.println("[CreateListing] Warning: Using user.dir as fallback: " + workspacePath);
            }
            
            // Build absolute path to source directories
            File workspaceDir = new File(workspacePath);
            File sourceDirPrimary = new File(workspaceDir, "src" + File.separator + "main" + File.separator + "webapp" + File.separator + UPLOAD_DIR_PRIMARY.replace("/", File.separator));
            File sourceDirOther = new File(workspaceDir, "src" + File.separator + "main" + File.separator + "webapp" + File.separator + UPLOAD_DIR_OTHER.replace("/", File.separator));
            sourcePath = sourceDirPrimary.getAbsolutePath();
            
            System.out.println("[CreateListing] Workspace dir: " + workspaceDir.getAbsolutePath());
            System.out.println("[CreateListing] Primary image path (absolute): " + sourcePath);
            System.out.println("[CreateListing] Other images path (absolute): " + sourceDirOther.getAbsolutePath());
            
            // Also get deployment paths (for copying later)
            realPath = getServletContext().getRealPath("/");
            String deployPathPrimary = null;
            String deployPathOther = null;
            if (realPath != null && !realPath.isEmpty()) {
                deployPathPrimary = realPath + UPLOAD_DIR_PRIMARY.replace("/", File.separator);
                deployPathOther = realPath + UPLOAD_DIR_OTHER.replace("/", File.separator);
            } else {
                realPath = getServletContext().getRealPath("");
                if (realPath != null && !realPath.isEmpty()) {
                    deployPathPrimary = realPath + File.separator + UPLOAD_DIR_PRIMARY.replace("/", File.separator);
                    deployPathOther = realPath + File.separator + UPLOAD_DIR_OTHER.replace("/", File.separator);
                }
            }
            
            // Create directories if they don't exist - use absolute paths
            File uploadDirPrimary = new File(sourcePath);
            File uploadDirOther = new File(sourceDirOther.getAbsolutePath());
            
            System.out.println("[CreateListing] Primary upload directory: " + uploadDirPrimary.getAbsolutePath());
            System.out.println("[CreateListing] Other upload directory: " + uploadDirOther.getAbsolutePath());
            
            // Create primary directory
            if (!uploadDirPrimary.exists()) {
                System.out.println("[CreateListing] Creating primary directory...");
                boolean created = uploadDirPrimary.mkdirs();
                if (!created) {
                    System.err.println("[CreateListing] ERROR: Failed to create primary directory");
                    sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                             "アップロードディレクトリの作成に失敗しました");
                    conn.rollback();
                    return;
                }
            }
            
            // Create other directory
            if (!uploadDirOther.exists()) {
                System.out.println("[CreateListing] Creating other images directory...");
                boolean created = uploadDirOther.mkdirs();
                if (!created) {
                    System.err.println("[CreateListing] ERROR: Failed to create other images directory");
                    sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                             "アップロードディレクトリの作成に失敗しました");
                    conn.rollback();
                    return;
                }
            }
            
            // Check if directories are writable
            if (!uploadDirPrimary.canWrite()) {
                System.err.println("[CreateListing] ERROR: Primary directory is not writable!");
                sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                         "アップロードディレクトリに書き込み権限がありません");
                conn.rollback();
                return;
            }
            
            if (!uploadDirOther.canWrite()) {
                System.err.println("[CreateListing] ERROR: Other images directory is not writable!");
                sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                         "アップロードディレクトリに書き込み権限がありません");
                conn.rollback();
                return;
            }
            
            System.out.println("[CreateListing] ✓ Directories are writable");
            
            int imageOrder = 0;
            String primaryImageUrl = null;
            
            // Handle image uploads
            System.out.println("[CreateListing] Processing image uploads...");
            Collection<Part> parts = request.getParts();
            System.out.println("[CreateListing] Total parts: " + parts.size());
            
            for (Part part : parts) {
                String partName = part.getName();
                System.out.println("[CreateListing] Part name: " + partName + ", Size: " + part.getSize() + 
                                 ", Content-Type: " + part.getContentType());
                
                if ("images".equals(partName) && part.getSize() > 0) {
                    String originalFileName = getFileName(part);
                    System.out.println("[CreateListing] Original filename: " + originalFileName);
                    
                    if (originalFileName == null || originalFileName.isEmpty() || 
                        originalFileName.equals("unknown_" + System.currentTimeMillis())) {
                        System.out.println("[CreateListing] Skipping invalid file: " + originalFileName);
                        continue; // Skip invalid files
                    }
                    
                    // Validate file type
                    String contentType = part.getContentType();
                    if (contentType == null || !contentType.startsWith("image/")) {
                        System.out.println("[CreateListing] Skipping non-image file: " + contentType);
                        continue;
                    }
                    
                    // Generate unique filename
                    String fileExtension = "";
                    int lastDot = originalFileName.lastIndexOf('.');
                    if (lastDot > 0) {
                        fileExtension = originalFileName.substring(lastDot);
                    }
                    String fileName = UUID.randomUUID().toString() + fileExtension;
                    
                    // First image goes to img/products, others go to images
                    String currentUploadDir;
                    String imageUrl;
                    String currentUploadPath;
                    if (imageOrder == 0) {
                        // First image - img/products
                        currentUploadDir = UPLOAD_DIR_PRIMARY;
                        currentUploadPath = sourcePath;
                        imageUrl = UPLOAD_DIR_PRIMARY + "/" + fileName;
                    } else {
                        // Other images - images
                        currentUploadDir = UPLOAD_DIR_OTHER;
                        currentUploadPath = sourceDirOther.getAbsolutePath();
                        imageUrl = UPLOAD_DIR_OTHER + "/" + fileName;
                    }
                    
                    String filePath = currentUploadPath + File.separator + fileName;
                    
                    System.out.println("[CreateListing] Saving image: " + fileName);
                    System.out.println("[CreateListing] Full path: " + filePath);
                    
                    // Save file
                    try {
                        // Ensure parent directory exists
                        File parentDir = new File(currentUploadPath);
                        if (!parentDir.exists()) {
                            boolean created = parentDir.mkdirs();
                            System.out.println("[CreateListing] Parent directory created: " + created);
                        }
                        
                        // Use InputStream to save file manually for better control
                        java.io.InputStream fileContent = part.getInputStream();
                        System.out.println("[CreateListing] InputStream obtained, starting file write...");
                        
                        java.io.FileOutputStream outputStream = new java.io.FileOutputStream(filePath);
                        System.out.println("[CreateListing] FileOutputStream created for: " + filePath);
                        
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
                        
                        System.out.println("[CreateListing] Stream closed. Total bytes written: " + totalBytesWritten);
                        
                        // Verify file was saved
                        File savedFile = new File(filePath);
                        System.out.println("[CreateListing] Checking if file exists: " + savedFile.exists());
                        System.out.println("[CreateListing] File absolute path: " + savedFile.getAbsolutePath());
                        
                        if (!savedFile.exists()) {
                            System.err.println("[CreateListing] ERROR: File was not saved! Path: " + filePath);
                            System.err.println("[CreateListing] Absolute path: " + savedFile.getAbsolutePath());
                            continue; // Skip this file
                        }
                        
                        long fileSize = savedFile.length();
                        System.out.println("[CreateListing] File exists! Size: " + fileSize + " bytes");
                        
                        if (fileSize == 0) {
                            System.err.println("[CreateListing] ERROR: Saved file is empty!");
                            savedFile.delete();
                            continue; // Skip this file
                        }
                        
                        if (fileSize != part.getSize()) {
                            System.err.println("[CreateListing] WARNING: File size mismatch! Expected: " + 
                                             part.getSize() + ", Got: " + fileSize);
                        }
                        
                        System.out.println("[CreateListing] File saved successfully. Size: " + fileSize + " bytes");
                        System.out.println("[CreateListing] File can be read: " + savedFile.canRead());
                        System.out.println("[CreateListing] File can be written: " + savedFile.canWrite());
                        
                        // Also save to deployment directory if different from source
                        String currentDeployPath = (imageOrder == 0) ? deployPathPrimary : deployPathOther;
                        if (currentDeployPath != null && !currentUploadPath.equals(currentDeployPath)) {
                            try {
                                File deployDir = new File(currentDeployPath);
                                if (!deployDir.exists()) {
                                    deployDir.mkdirs();
                                }
                                
                                String deployFilePath = currentDeployPath + File.separator + fileName;
                                java.io.FileInputStream sourceStream = new java.io.FileInputStream(savedFile);
                                java.io.FileOutputStream deployStream = new java.io.FileOutputStream(deployFilePath);
                                
                                byte[] copyBuffer = new byte[8192];
                                int copyBytesRead;
                                while ((copyBytesRead = sourceStream.read(copyBuffer)) != -1) {
                                    deployStream.write(copyBuffer, 0, copyBytesRead);
                                }
                                
                                deployStream.close();
                                sourceStream.close();
                                
                                System.out.println("[CreateListing] File also copied to deployment directory: " + deployFilePath);
                            } catch (Exception e) {
                                System.err.println("[CreateListing] Warning: Failed to copy to deployment directory: " + e.getMessage());
                                // Continue anyway - source file is saved
                            }
                        }
                        
                        // Save to database - imageUrl already set above
                        if (imageOrder == 0) {
                            primaryImageUrl = imageUrl;
                        }
                        
                        String imageSql = "INSERT INTO product_images " +
                                "(product_id, image_url, image_order, is_primary) " +
                                "VALUES (?, ?, ?, ?)";
                        
                        stmt = conn.prepareStatement(imageSql);
                        stmt.setInt(1, productId);
                        stmt.setString(2, imageUrl);
                        stmt.setInt(3, imageOrder);
                        stmt.setBoolean(4, imageOrder == 0);
                        stmt.executeUpdate();
                        stmt.close();
                        
                        System.out.println("[CreateListing] Image saved to database: " + imageUrl);
                        imageOrder++;
                    } catch (IOException e) {
                        System.err.println("[CreateListing] Error saving file: " + e.getMessage());
                        e.printStackTrace();
                        continue; // Skip this file and continue with others
                    }
                }
            }
            
            System.out.println("[CreateListing] Total images uploaded: " + imageOrder);
            
            // Update product with primary image
            if (primaryImageUrl != null) {
                String updateImageSql = "UPDATE products SET image_url = ? WHERE product_id = ?";
                stmt = conn.prepareStatement(updateImageSql);
                stmt.setString(1, primaryImageUrl);
                stmt.setInt(2, productId);
                stmt.executeUpdate();
                stmt.close();
            }
            
            // Add product specification if brand provided
            if (brand != null && !brand.trim().isEmpty()) {
                String specSql = "INSERT INTO product_specifications " +
                        "(product_id, spec_name, spec_value, display_order) " +
                        "VALUES (?, 'Brand', ?, 1)";
                stmt = conn.prepareStatement(specSql);
                stmt.setInt(1, productId);
                stmt.setString(2, brand);
                stmt.executeUpdate();
                stmt.close();
            }
            
            // Commit transaction
            conn.commit();
            
            System.out.println("CreateListing - Product created successfully: ID=" + productId);
            
            // Send success response
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "商品が正常に登録されました");
            result.put("product_id", productId);
            result.put("slug", slug);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException | NumberFormatException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                     "データベースエラー: " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    /**
     * Update existing product listing
     */
    private void updateListing(HttpServletRequest request, HttpServletResponse response) 
            throws IOException, ServletException {
        
        HttpSession session = request.getSession(false);
        
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "ログインが必要です");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        String productIdParam = request.getParameter("product_id");
        
        if (productIdParam == null || productIdParam.isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "商品IDが必要です");
            return;
        }
        
        int productId = Integer.parseInt(productIdParam);
        
        Connection conn = null;
        PreparedStatement stmt = null;
        PreparedStatement setUtf8 = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            setUtf8 = conn.prepareStatement("SET NAMES utf8mb4");
            setUtf8.execute();
            setUtf8.close();
            
            conn.setAutoCommit(false);
            
            String checkSql = "SELECT user_id FROM products WHERE product_id = ?";
            stmt = conn.prepareStatement(checkSql);
            stmt.setInt(1, productId);
            rs = stmt.executeQuery();
            
            if (!rs.next() || rs.getInt("user_id") != userId) {
                conn.rollback();
                sendError(response, HttpServletResponse.SC_FORBIDDEN, "この商品を編集する権限がありません");
                return;
            }
            
            rs.close();
            stmt.close();
            
            String productName = getParameterSafe(request, "product_name");
            String categoryIdStr = request.getParameter("category_id");
            String description = getParameterSafe(request, "description");
            String condition = request.getParameter("condition");
            String priceStr = request.getParameter("price");
            String stockStr = request.getParameter("stock_quantity");
            String isRentalStr = request.getParameter("is_rental");
            String rentalPriceStr = request.getParameter("rental_price_daily");
            
            if (productName == null || productName.trim().isEmpty() ||
                categoryIdStr == null || description == null || 
                condition == null || priceStr == null || stockStr == null) {
                conn.rollback();
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "必須フィールドが不足しています");
                return;
            }
            
            int categoryId = Integer.parseInt(categoryIdStr);
            double price = Double.parseDouble(priceStr);
            int stock = Integer.parseInt(stockStr);
            boolean isRental = "1".equals(isRentalStr) || "true".equals(isRentalStr) || "on".equals(isRentalStr);
            
            String updateSql = """
                UPDATE products 
                SET product_name = ?, description = ?, price = ?, stock_quantity = ?,
                    `condition` = ?, is_rental = ?, rental_price_daily = ?, updated_at = NOW()
                WHERE product_id = ? AND user_id = ?
                """;
            
            stmt = conn.prepareStatement(updateSql);
            stmt.setString(1, productName);
            stmt.setString(2, description);
            stmt.setDouble(3, price);
            stmt.setInt(4, stock);
            stmt.setString(5, mapCondition(condition));
            stmt.setBoolean(6, isRental);
            
            if (isRental && rentalPriceStr != null && !rentalPriceStr.isEmpty()) {
                stmt.setDouble(7, Double.parseDouble(rentalPriceStr));
            } else {
                stmt.setNull(7, java.sql.Types.DOUBLE);
            }
            
            stmt.setInt(8, productId);
            stmt.setInt(9, userId);
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected == 0) {
                conn.rollback();
                sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                         "商品の更新に失敗しました");
                return;
            }
            
            stmt.close();
            
            String deleteCategorySql = "DELETE FROM product_categories WHERE product_id = ?";
            stmt = conn.prepareStatement(deleteCategorySql);
            stmt.setInt(1, productId);
            stmt.executeUpdate();
            stmt.close();
            
            String categorySql = "INSERT INTO product_categories (product_id, category_id) VALUES (?, ?)";
            stmt = conn.prepareStatement(categorySql);
            stmt.setInt(1, productId);
            stmt.setInt(2, categoryId);
            stmt.executeUpdate();
            stmt.close();
            
            conn.commit();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "商品を更新しました");
            result.put("product_id", productId);
            
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json; charset=UTF-8");
            PrintWriter out = response.getWriter();
            out.print(gson.toJson(result));
            out.flush();
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                     "データベースエラー: " + e.getMessage());
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                     "サーバーエラー: " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    /**
     * Get parameter with UTF-8 support
     */
    private String getParameterSafe(HttpServletRequest request, String paramName) {
        String value = request.getParameter(paramName);
        if (value == null) {
            return null;
        }
        
        // Parameter sudah di-decode oleh servlet container dengan encoding yang benar
        // karena kita sudah set request.setCharacterEncoding("UTF-8")
        return value;
    }
    
    /**
     * Generate URL-friendly slug from product name
     */
    private String generateSlug(String productName) {
        // For Japanese text, use romanization or just use UUID
        // Simple approach: replace non-alphanumeric with dash
        String slug = productName.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
        
        // If slug is empty (all Japanese characters), use product name hash
        if (slug.isEmpty()) {
            slug = "product";
        }
        
        // Add random suffix to ensure uniqueness
        slug += "-" + UUID.randomUUID().toString().substring(0, 8);
        
        return slug;
    }
    
    /**
     * Map condition from form to database format
     */
    private String mapCondition(String condition) {
        if (condition == null) {
            return "good";
        }
        
        switch (condition.toLowerCase()) {
            case "new":
            case "新品":
                return "new";
            case "like-new":
            case "like_new":
            case "ほぼ新品":
                return "like_new";
            case "good":
            case "良い":
                return "good";
            case "acceptable":
            case "fair":
            case "可":
                return "fair";
            case "poor":
            case "悪い":
                return "poor";
            default:
                return "good";
        }
    }
    
    /**
     * Extract filename from Part
     */
    private String getFileName(Part part) {
        String contentDisposition = part.getHeader("content-disposition");
        
        if (contentDisposition != null) {
            for (String token : contentDisposition.split(";")) {
                if (token.trim().startsWith("filename")) {
                    String filename = token.substring(token.indexOf('=') + 1).trim()
                            .replace("\"", "");
                    
                    // Handle UTF-8 encoded filenames
                    try {
                        // If filename contains non-ASCII characters, it might be encoded
                        return new String(filename.getBytes("ISO-8859-1"), "UTF-8");
                    } catch (Exception e) {
                        return filename;
                    }
                }
            }
        }
        
        return "unknown_" + System.currentTimeMillis();
    }
    
    /**
     * Send JSON response
     */
    private void sendJsonResponse(HttpServletResponse response, Object data) 
            throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(data));
        out.flush();
    }
    
    /**
     * Send error response
     */
    private void sendError(HttpServletResponse response, int status, String message) 
            throws IOException {
        response.setStatus(status);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        error.put("message", message);
        error.put("status", status);
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(error));
        out.flush();
    }
}