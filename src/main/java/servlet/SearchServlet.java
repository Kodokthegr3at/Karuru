package servlet;

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

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;

import util.DatabaseConnection;

/**
 * Servlet for handling advanced product search
 * Endpoint: /SearchServlet
 */
@WebServlet("/SearchServlet")
public class SearchServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Gson gson = new Gson();
    private static final int ITEMS_PER_PAGE = 20;

    public SearchServlet() {
        super();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Configure UTF-8 encoding
        util.FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        // Set CORS headers if needed
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        
        try {
            String action = request.getParameter("action");
            if ("suggestions".equals(action)) {
                getSearchSuggestions(request, response);
            } else {
                searchProducts(request, response);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                     "Server error: " + e.getMessage());
        }
    }
    
    /**
     * Advanced product search with filters
     */
    private void searchProducts(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        // Get search parameters
        String query = request.getParameter("query");
        String categories = request.getParameter("categories");
        String priceRange = request.getParameter("price");
        String conditions = request.getParameter("conditions");
        String sortBy = request.getParameter("sort");
        
        int page = 1;
        try {
            String pageParam = request.getParameter("page");
            if (pageParam != null && !pageParam.isEmpty()) {
                page = Integer.parseInt(pageParam);
            }
        } catch (NumberFormatException e) {
            page = 1;
        }
        
        int offset = (page - 1) * ITEMS_PER_PAGE;
        
        System.out.println("=== Search Request ===");
        System.out.println("Query: " + query);
        System.out.println("Categories: " + categories);
        System.out.println("Price Range: " + priceRange);
        System.out.println("Conditions: " + conditions);
        System.out.println("Sort: " + sortBy);
        System.out.println("Page: " + page);
        
        // Allow empty query - will show all available products with filters
        // if (query == null || query.trim().isEmpty()) {
        //     sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Search query is required");
        //     return;
        // }
        
        List<Map<String, Object>> products = new ArrayList<>();
        int totalCount = 0;
        // Initialize categories and sellers lists before try block
        List<Map<String, Object>> foundCategories = new ArrayList<>();
        List<Map<String, Object>> foundSellers = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        PreparedStatement countStmt = null;
        ResultSet rs = null;
        ResultSet countRs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Build SQL query dynamically - always search products by name/description
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("SELECT p.product_id, p.product_name, p.description, p.price, ");
            sqlBuilder.append("p.original_price, p.status, p.image_url, p.is_rental, ");
            sqlBuilder.append("p.rental_price_daily, p.condition, p.views_count, p.likes_count, ");
            sqlBuilder.append("p.user_id as seller_id, p.created_at, u.username as seller_name, ");
            sqlBuilder.append("u.user_id, ");
            sqlBuilder.append("p.rating_avg, p.rating_count ");
            sqlBuilder.append("FROM products p ");
            sqlBuilder.append("LEFT JOIN users u ON p.user_id = u.user_id ");
            
            // Add category join if needed for category filter
            boolean hasCategoryJoin = false;
            if (categories != null && !categories.trim().isEmpty()) {
                sqlBuilder.append("INNER JOIN product_categories pc ON p.product_id = pc.product_id ");
                sqlBuilder.append("INNER JOIN categories c ON pc.category_id = c.category_id ");
                hasCategoryJoin = true;
            }
            
            sqlBuilder.append("WHERE p.status = 'available' ");
            
            // Always search in product name and description when query is provided
            if (query != null && !query.trim().isEmpty()) {
                sqlBuilder.append("AND (LOWER(p.product_name) LIKE LOWER(?) ");
                sqlBuilder.append("OR LOWER(p.description) LIKE LOWER(?)) ");
            }
            
            // Add category filter - support both category_id and category_name
            if (categories != null && !categories.trim().isEmpty()) {
                String[] categoryArray = categories.split(",");
                if (categoryArray.length > 0) {
                    sqlBuilder.append("AND (");
                    for (int i = 0; i < categoryArray.length; i++) {
                        if (i > 0) sqlBuilder.append(" OR ");
                        String cat = categoryArray[i].trim();
                        // Check if it's a number (category_id) or string (category_name)
                        try {
                            Integer.parseInt(cat);
                            // It's a category_id
                            sqlBuilder.append("c.category_id = ?");
                        } catch (NumberFormatException e) {
                            // It's a category_name
                            sqlBuilder.append("LOWER(c.category_name) = LOWER(?)");
                        }
                    }
                    sqlBuilder.append(") ");
                }
            }
            
            // Add price range filter - HANYA menentukan struktur SQL
            if (priceRange != null && !priceRange.trim().isEmpty()) {
                String[] priceParts = priceRange.split("-");
                if (priceParts.length == 2) {
                    if (priceParts[1].equals("+")) {
                        sqlBuilder.append("AND p.price >= ? ");
                    } else {
                        sqlBuilder.append("AND p.price BETWEEN ? AND ? ");
                    }
                }
            }
            
            // Add condition filter
            if (conditions != null && !conditions.trim().isEmpty()) {
                String[] conditionArray = conditions.split(",");
                if (conditionArray.length > 0) {
                    sqlBuilder.append("AND (");
                    for (int i = 0; i < conditionArray.length; i++) {
                        if (i > 0) sqlBuilder.append(" OR ");
                        sqlBuilder.append("p.condition = ?");
                    }
                    sqlBuilder.append(") ");
                }
            }
            
            // Add sorting
            sqlBuilder.append("ORDER BY ");
            if (sortBy != null && !sortBy.trim().isEmpty()) {
                switch (sortBy) {
                    case "price-low":
                        sqlBuilder.append("p.price ASC ");
                        break;
                    case "price-high":
                        sqlBuilder.append("p.price DESC ");
                        break;
                    case "popular":
                        sqlBuilder.append("(p.views_count + p.likes_count * 2) DESC ");
                        break;
                    case "newest":
                    default:
                        sqlBuilder.append("p.created_at DESC ");
                        break;
                }
            } else {
                sqlBuilder.append("p.created_at DESC ");
            }
            
            sqlBuilder.append("LIMIT ? OFFSET ?");
            
            String sql = sqlBuilder.toString();
            System.out.println("SQL: " + sql);
            
            // Prepare statement and set parameters
            stmt = conn.prepareStatement(sql);
            
            int paramIndex = 1;
            // Set search parameters only if query is provided
            // Always search in product name and description
            if (query != null && !query.trim().isEmpty()) {
                String searchPattern = "%" + query + "%";
                stmt.setString(paramIndex++, searchPattern);
                stmt.setString(paramIndex++, searchPattern);
            }
            
            // Set category parameters - support both category_id and category_name
            if (categories != null && !categories.trim().isEmpty()) {
                String[] categoryArray = categories.split(",");
                for (String cat : categoryArray) {
                    String catTrimmed = cat.trim();
                    try {
                        // Try to parse as integer (category_id)
                        int categoryId = Integer.parseInt(catTrimmed);
                        stmt.setInt(paramIndex++, categoryId);
                    } catch (NumberFormatException e) {
                        // It's a category_name
                        stmt.setString(paramIndex++, catTrimmed);
                    }
                }
            }
            
            // Set price parameters - PARSE dan SET nilai di sini saja
            if (priceRange != null && !priceRange.trim().isEmpty()) {
                String[] priceParts = priceRange.split("-");
                if (priceParts.length == 2) {
                    try {
                        double minPrice = Double.parseDouble(priceParts[0]);
                        stmt.setDouble(paramIndex++, minPrice);
                        
                        if (!priceParts[1].equals("+")) {
                            double maxPrice = Double.parseDouble(priceParts[1]);
                            stmt.setDouble(paramIndex++, maxPrice);
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid price range format: " + priceRange);
                        // Skip invalid price range - kita tetap lanjut tanpa filter price
                    }
                }
            }
            
            // Set condition parameters
            if (conditions != null && !conditions.trim().isEmpty()) {
                String[] conditionArray = conditions.split(",");
                for (String cond : conditionArray) {
                    String mappedCondition = mapCondition(cond.trim());
                    stmt.setString(paramIndex++, mappedCondition);
                }
            }
            
            // Set pagination parameters
            stmt.setInt(paramIndex++, ITEMS_PER_PAGE);
            stmt.setInt(paramIndex++, offset);
            
            // Execute search query
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> product = mapProductFromResultSet(rs);
                // Get product images
                int productId = rs.getInt("product_id");
                List<String> images = getProductImages(conn, productId);
                product.put("images", images);
                products.add(product);
            }
            
            // Get total count (only on first page for performance)
            if (page == 1) {
                String countSql = buildCountQuery(query, categories, priceRange, conditions, "product");
                countStmt = conn.prepareStatement(countSql);
                
                // Set parameters for count query (same as main query but without pagination)
                paramIndex = 1;
                // Set search parameters only if query is provided
                if (query != null && !query.trim().isEmpty()) {
                    String searchPattern = "%" + query + "%";
                    countStmt.setString(paramIndex++, searchPattern);
                    countStmt.setString(paramIndex++, searchPattern);
                }
                
                if (categories != null && !categories.trim().isEmpty()) {
                    String[] categoryArray = categories.split(",");
                    for (String cat : categoryArray) {
                        String catTrimmed = cat.trim();
                        try {
                            // Try to parse as integer (category_id)
                            int categoryId = Integer.parseInt(catTrimmed);
                            countStmt.setInt(paramIndex++, categoryId);
                        } catch (NumberFormatException e) {
                            // It's a category_name
                            countStmt.setString(paramIndex++, catTrimmed);
                        }
                    }
                }
                
                if (priceRange != null && !priceRange.trim().isEmpty()) {
                    String[] priceParts = priceRange.split("-");
                    if (priceParts.length == 2) {
                        try {
                            double minPrice = Double.parseDouble(priceParts[0]);
                            countStmt.setDouble(paramIndex++, minPrice);
                            if (!priceParts[1].equals("+")) {
                                double maxPrice = Double.parseDouble(priceParts[1]);
                                countStmt.setDouble(paramIndex++, maxPrice);
                            }
                        } catch (NumberFormatException e) {
                            // Skip invalid price range
                        }
                    }
                }
                
                if (conditions != null && !conditions.trim().isEmpty()) {
                    String[] conditionArray = conditions.split(",");
                    for (String cond : conditionArray) {
                        String mappedCondition = mapCondition(cond.trim());
                        countStmt.setString(paramIndex++, mappedCondition);
                    }
                }
                
                countRs = countStmt.executeQuery();
                if (countRs.next()) {
                    totalCount = countRs.getInt("total");
                }
            }
            
            // If query is provided, also search for categories and sellers
            if (query != null && !query.trim().isEmpty()) {
                // Search categories
                String categorySql = "SELECT DISTINCT c.category_id, c.category_name, c.description, c.image_url, c.icon_url " +
                        "FROM categories c " +
                        "WHERE c.is_active = 1 " +
                        "AND LOWER(c.category_name) LIKE LOWER(?) " +
                        "ORDER BY c.display_order ASC, c.category_name ASC " +
                        "LIMIT 10";
                
                try (PreparedStatement categoryStmt = conn.prepareStatement(categorySql)) {
                    categoryStmt.setString(1, "%" + query + "%");
                    try (ResultSet categoryRs = categoryStmt.executeQuery()) {
                        while (categoryRs.next()) {
                            Map<String, Object> category = new HashMap<>();
                            category.put("category_id", categoryRs.getInt("category_id"));
                            category.put("category_name", categoryRs.getString("category_name"));
                            category.put("description", categoryRs.getString("description"));
                            category.put("image_url", categoryRs.getString("image_url"));
                            category.put("icon_url", categoryRs.getString("icon_url"));
                            foundCategories.add(category);
                        }
                    }
                }
                
                System.out.println("Found categories: " + foundCategories.size());
                
                // Search sellers
                String sellerSql = "SELECT DISTINCT u.user_id, u.username, " +
                        "COUNT(DISTINCT p.product_id) as product_count " +
                        "FROM users u " +
                        "INNER JOIN products p ON u.user_id = p.user_id " +
                        "WHERE p.status = 'available' " +
                        "AND (LOWER(u.username) LIKE LOWER(?) OR LOWER(u.email) LIKE LOWER(?)) " +
                        "GROUP BY u.user_id, u.username " +
                        "ORDER BY product_count DESC, u.username ASC " +
                        "LIMIT 10";
                
                try (PreparedStatement sellerStmt = conn.prepareStatement(sellerSql)) {
                    String searchPattern = "%" + query + "%";
                    sellerStmt.setString(1, searchPattern);
                    sellerStmt.setString(2, searchPattern);
                    try (ResultSet sellerRs = sellerStmt.executeQuery()) {
                        while (sellerRs.next()) {
                            Map<String, Object> seller = new HashMap<>();
                            seller.put("seller_id", sellerRs.getInt("user_id"));
                            seller.put("user_id", sellerRs.getInt("user_id"));
                            seller.put("username", sellerRs.getString("username"));
                            seller.put("product_count", sellerRs.getInt("product_count"));
                            foundSellers.add(seller);
                        }
                    }
                }
                
                System.out.println("Found sellers: " + foundSellers.size());
            }
            
            System.out.println("Search results: " + products.size() + " products found");
            System.out.println("Total count: " + totalCount);
            System.out.println("Found categories: " + foundCategories.size());
            System.out.println("Found sellers: " + foundSellers.size());
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("Database error in searchProducts: " + e.getMessage());
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                     "Search error: " + e.getMessage());
            return;
        } finally {
            DatabaseConnection.closeResources(countRs, countStmt, null);
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        // Prepare response
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("products", products);
        responseData.put("totalCount", totalCount);
        responseData.put("page", page);
        responseData.put("hasMore", products.size() >= ITEMS_PER_PAGE);
        
        // Always include categories and sellers (empty arrays if no query or no results)
        responseData.put("categories", foundCategories);
        responseData.put("sellers", foundSellers);
        responseData.put("categoriesCount", foundCategories.size());
        responseData.put("sellersCount", foundSellers.size());
        
        System.out.println("Response data - Products: " + products.size() + 
                          ", Categories: " + foundCategories.size() + 
                          ", Sellers: " + foundSellers.size());
        
        sendJsonResponse(response, responseData);
    }
    
    /**
     * Get search suggestions for autocomplete
     */
    private void getSearchSuggestions(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        String query = request.getParameter("query");
        String searchType = request.getParameter("type");
        if (searchType == null) searchType = "product";
        
        int limit = 8;
        try {
            String limitParam = request.getParameter("limit");
            if (limitParam != null && !limitParam.isEmpty()) {
                limit = Integer.parseInt(limitParam);
            }
        } catch (NumberFormatException e) {
            limit = 8;
        }
        
        if (query == null || query.trim().isEmpty() || query.length() < 2) {
            Map<String, Object> result = new HashMap<>();
            result.put("suggestions", new ArrayList<>());
            sendJsonResponse(response, result);
            return;
        }
        
        List<Map<String, Object>> suggestions = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            String searchPattern = "%" + query + "%";
            
            // Always get suggestions from all types when no specific type is requested
            // or when type is "all" or not specified
            if ("category".equals(searchType)) {
                // Get category name suggestions
                String categorySql = """
                    SELECT DISTINCT c.category_name, c.category_id
                    FROM categories c
                    WHERE c.is_active = 1
                    AND LOWER(c.category_name) LIKE LOWER(?)
                    ORDER BY c.display_order ASC, c.category_name ASC
                    LIMIT ?
                    """;
                
                stmt = conn.prepareStatement(categorySql);
                stmt.setString(1, searchPattern);
                stmt.setInt(2, limit);
                rs = stmt.executeQuery();
                
                while (rs.next()) {
                    Map<String, Object> suggestion = new HashMap<>();
                    String categoryName = rs.getString("category_name");
                    suggestion.put("type", "category");
                    suggestion.put("text", categoryName);
                    suggestion.put("name", categoryName);
                    suggestion.put("id", rs.getInt("category_id"));
                    suggestion.put("category_id", rs.getInt("category_id"));
                    
                    // Highlight matching text
                    String highlight = highlightMatch(categoryName, query);
                    suggestion.put("highlight", highlight);
                    
                    suggestions.add(suggestion);
                }
                rs.close();
                stmt.close();
            } else if ("seller".equals(searchType) || "user".equals(searchType)) {
                // Get seller/user name suggestions
                String sellerSql = """
                    SELECT DISTINCT u.username, u.user_id
                    FROM users u
                    INNER JOIN products p ON u.user_id = p.user_id
                    WHERE p.status = 'available'
                    AND LOWER(u.username) LIKE LOWER(?)
                    ORDER BY u.username ASC
                    LIMIT ?
                    """;
                
                stmt = conn.prepareStatement(sellerSql);
                stmt.setString(1, searchPattern);
                stmt.setInt(2, limit);
                rs = stmt.executeQuery();
                
                while (rs.next()) {
                    Map<String, Object> suggestion = new HashMap<>();
                    String username = rs.getString("username");
                    suggestion.put("type", "seller");
                    suggestion.put("text", username);
                    suggestion.put("name", username);
                    suggestion.put("id", rs.getInt("user_id"));
                    suggestion.put("seller_id", rs.getInt("user_id"));
                    
                    // Highlight matching text
                    String highlight = highlightMatch(username, query);
                    suggestion.put("highlight", highlight);
                    
                    suggestions.add(suggestion);
                }
                rs.close();
                stmt.close();
            } else {
                // Default: Get all types of suggestions (products, categories, sellers)
                int limitPerType = Math.max(3, limit / 3); // Divide limit among types
                
                // Get product name suggestions
                String productSql = """
                    SELECT DISTINCT p.product_name, p.product_id
                    FROM products p
                    WHERE p.status = 'available'
                    AND LOWER(p.product_name) LIKE LOWER(?)
                    ORDER BY p.views_count DESC, p.likes_count DESC, p.product_name ASC
                    LIMIT ?
                    """;
                
                stmt = conn.prepareStatement(productSql);
                stmt.setString(1, searchPattern);
                stmt.setInt(2, limitPerType);
                rs = stmt.executeQuery();
                
                while (rs.next()) {
                    Map<String, Object> suggestion = new HashMap<>();
                    String productName = rs.getString("product_name");
                    suggestion.put("type", "product");
                    suggestion.put("text", productName);
                    suggestion.put("name", productName);
                    suggestion.put("id", rs.getInt("product_id"));
                    
                    // Highlight matching text
                    String highlight = highlightMatch(productName, query);
                    suggestion.put("highlight", highlight);
                    
                    suggestions.add(suggestion);
                }
                rs.close();
                stmt.close();
                
                // Get category suggestions if we have space
                if (suggestions.size() < limit) {
                    String categorySql = """
                        SELECT DISTINCT c.category_name, c.category_id
                        FROM categories c
                        WHERE c.is_active = 1
                        AND LOWER(c.category_name) LIKE LOWER(?)
                        ORDER BY c.display_order ASC, c.category_name ASC
                        LIMIT ?
                        """;
                    
                    int remaining = limit - suggestions.size();
                    stmt = conn.prepareStatement(categorySql);
                    stmt.setString(1, searchPattern);
                    stmt.setInt(2, remaining);
                    rs = stmt.executeQuery();
                    
                    while (rs.next()) {
                        Map<String, Object> suggestion = new HashMap<>();
                        String categoryName = rs.getString("category_name");
                        suggestion.put("type", "category");
                        suggestion.put("text", categoryName);
                        suggestion.put("name", categoryName);
                        suggestion.put("id", rs.getInt("category_id"));
                        suggestion.put("category_id", rs.getInt("category_id"));
                        
                        // Highlight matching text
                        String highlight = highlightMatch(categoryName, query);
                        suggestion.put("highlight", highlight);
                        
                        suggestions.add(suggestion);
                    }
                    rs.close();
                    stmt.close();
                }
                
                // Get seller suggestions if we still have space
                if (suggestions.size() < limit) {
                    String sellerSql = """
                        SELECT DISTINCT u.username, u.user_id
                        FROM users u
                        INNER JOIN products p ON u.user_id = p.user_id
                        WHERE p.status = 'available'
                        AND (LOWER(u.username) LIKE LOWER(?) OR LOWER(u.email) LIKE LOWER(?))
                        ORDER BY u.username ASC
                        LIMIT ?
                        """;
                    
                    int remaining = limit - suggestions.size();
                    stmt = conn.prepareStatement(sellerSql);
                    stmt.setString(1, searchPattern);
                    stmt.setString(2, searchPattern);
                    stmt.setInt(3, remaining);
                    rs = stmt.executeQuery();
                    
                    while (rs.next()) {
                        Map<String, Object> suggestion = new HashMap<>();
                        String username = rs.getString("username");
                        suggestion.put("type", "seller");
                        suggestion.put("text", username);
                        suggestion.put("name", username);
                        suggestion.put("id", rs.getInt("user_id"));
                        suggestion.put("seller_id", rs.getInt("user_id"));
                        
                        // Highlight matching text
                        String highlight = highlightMatch(username, query);
                        suggestion.put("highlight", highlight);
                        
                        suggestions.add(suggestion);
                    }
                    rs.close();
                    stmt.close();
                }
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("Error getting suggestions: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("suggestions", suggestions);
        sendJsonResponse(response, result);
    }
    
    /**
     * Highlight matching text in suggestion
     */
    private String highlightMatch(String text, String query) {
        if (text == null || query == null) return text;
        
        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();
        int index = lowerText.indexOf(lowerQuery);
        
        if (index == -1) return text;
        
        String before = escapeHtml(text.substring(0, index));
        String match = escapeHtml(text.substring(index, index + query.length()));
        String after = escapeHtml(text.substring(index + query.length()));
        
        return before + "<strong>" + match + "</strong>" + after;
    }
    
    /**
     * Escape HTML for safe display
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    /**
     * Build count query for total results
     */
    private String buildCountQuery(String query, String categories, String priceRange, String conditions, String searchType) {
        if (searchType == null) searchType = "product";
        
        StringBuilder countBuilder = new StringBuilder();
        countBuilder.append("SELECT COUNT(DISTINCT p.product_id) as total ");
        countBuilder.append("FROM products p ");
        countBuilder.append("LEFT JOIN users u ON p.user_id = u.user_id ");
        
        boolean hasCategoryJoin = false;
        if (categories != null && !categories.trim().isEmpty()) {
            countBuilder.append("INNER JOIN product_categories pc ON p.product_id = pc.product_id ");
            countBuilder.append("INNER JOIN categories c ON pc.category_id = c.category_id ");
            hasCategoryJoin = true;
        }
        
        // Add category join for category search
        if ("category".equals(searchType) && !hasCategoryJoin) {
            countBuilder.append("INNER JOIN product_categories pc ON p.product_id = pc.product_id ");
            countBuilder.append("INNER JOIN categories c ON pc.category_id = c.category_id ");
            hasCategoryJoin = true;
        }
        
        countBuilder.append("WHERE p.status = 'available' ");
        
        // Always search in product name and description when query is provided
        if (query != null && !query.trim().isEmpty()) {
            countBuilder.append("AND (LOWER(p.product_name) LIKE LOWER(?) ");
            countBuilder.append("OR LOWER(p.description) LIKE LOWER(?)) ");
        }
        
        if (categories != null && !categories.trim().isEmpty()) {
            String[] categoryArray = categories.split(",");
            if (categoryArray.length > 0) {
                countBuilder.append("AND (");
                for (int i = 0; i < categoryArray.length; i++) {
                    if (i > 0) countBuilder.append(" OR ");
                    String cat = categoryArray[i].trim();
                    // Check if it's a number (category_id) or string (category_name)
                    try {
                        Integer.parseInt(cat);
                        // It's a category_id
                        countBuilder.append("c.category_id = ?");
                    } catch (NumberFormatException e) {
                        // It's a category_name
                        countBuilder.append("LOWER(c.category_name) = LOWER(?)");
                    }
                }
                countBuilder.append(") ");
            }
        }
        
        if (priceRange != null && !priceRange.trim().isEmpty()) {
            String[] priceParts = priceRange.split("-");
            if (priceParts.length == 2) {
                if (priceParts[1].equals("+")) {
                    countBuilder.append("AND p.price >= ? ");
                } else {
                    countBuilder.append("AND p.price BETWEEN ? AND ? ");
                }
            }
        }
        
        if (conditions != null && !conditions.trim().isEmpty()) {
            String[] conditionArray = conditions.split(",");
            if (conditionArray.length > 0) {
                countBuilder.append("AND (");
                for (int i = 0; i < conditionArray.length; i++) {
                    if (i > 0) countBuilder.append(" OR ");
                    countBuilder.append("p.condition = ?");
                }
                countBuilder.append(") ");
            }
        }
        
        return countBuilder.toString();
    }
    
    /**
     * Map condition from UI to database value
     */
    private String mapCondition(String uiCondition) {
        switch(uiCondition.toLowerCase()) {
            case "new":
                return "new";
            case "like-new":
            case "like_new":
                return "like_new";
            case "good":
                return "good";
            case "acceptable":
            case "fair":
                return "fair";
            default:
                return uiCondition;
        }
    }
    
    /**
     * Get product images from product_images table
     */
    private List<String> getProductImages(Connection conn, int productId) throws SQLException {
        List<String> images = new ArrayList<>();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            String sql = "SELECT image_url FROM product_images WHERE product_id = ? ORDER BY is_primary DESC, image_order ASC, image_id ASC";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, productId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                String imageUrl = rs.getString("image_url");
                if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                    images.add(imageUrl);
                }
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }
        
        return images;
    }
    
    /**
     * Map product data from ResultSet
     */
    private Map<String, Object> mapProductFromResultSet(ResultSet rs) throws SQLException {
        Map<String, Object> product = new HashMap<>();
        product.put("product_id", rs.getInt("product_id"));
        product.put("product_name", rs.getString("product_name"));
        product.put("description", rs.getString("description"));
        product.put("price", rs.getDouble("price"));
        product.put("original_price", rs.getDouble("original_price"));
        product.put("status", rs.getString("status"));
        product.put("image_url", rs.getString("image_url"));
        product.put("is_rental", rs.getBoolean("is_rental"));
        product.put("rental_price_daily", rs.getDouble("rental_price_daily"));
        product.put("condition", rs.getString("condition"));
        product.put("views_count", rs.getInt("views_count"));
        product.put("likes_count", rs.getInt("likes_count"));
        product.put("seller_id", rs.getInt("seller_id"));
        product.put("seller_name", rs.getString("seller_name"));
        product.put("rating_avg", rs.getDouble("rating_avg"));
        product.put("rating_count", rs.getInt("rating_count"));
        product.put("created_at", rs.getTimestamp("created_at").toString());
        return product;
    }
    
    /**
     * Send JSON response
     */
    private void sendJsonResponse(HttpServletResponse response, Object data) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        
        String json = gson.toJson(data);
        
        PrintWriter out = response.getWriter();
        out.print(json);
        out.flush();
    }
    
    /**
     * Send error response
     */
    private void sendError(HttpServletResponse response, int status, String message) 
            throws IOException {
        response.setStatus(status);
        
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("status", status);
        
        String json = gson.toJson(error);
        
        PrintWriter out = response.getWriter();
        out.print(json);
        out.flush();
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        doGet(request, response);
    }
    
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}