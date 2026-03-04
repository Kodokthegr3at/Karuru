package servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
import util.FilterEncodingUTF8;

@WebServlet({"/BannerServlet", "/Banner"})
public class BannerServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        String action = request.getParameter("action");
        
        try {
            if (action == null || action.isEmpty()) {
                action = "getActive"; // Default action
            }
            
            switch (action) {
                case "getActive":
                    getActiveBanners(request, response);
                    break;
                case "getAll":
                    getAllBanners(request, response);
                    break;
                case "getByPosition":
                    getBannersByPosition(request, response);
                    break;
                case "getById":
                    getBannerById(request, response);
                    break;
                default:
                    sendError(response, "Invalid action: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        String action = request.getParameter("action");
        
        try {
            if (action == null || action.isEmpty()) {
                sendError(response, "Action parameter is required");
                return;
            }
            
            switch (action) {
                case "create":
                    createBanner(request, response);
                    break;
                case "update":
                    updateBanner(request, response);
                    break;
                case "delete":
                    deleteBanner(request, response);
                    break;
                default:
                    sendError(response, "Invalid action: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    private void getActiveBanners(HttpServletRequest request, HttpServletResponse response) 
            throws IOException, SQLException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "SELECT banner_id, title, image_url, link_url, position, " +
                        "display_order, is_active, start_date, end_date, created_at " +
                        "FROM banners " +
                        "WHERE is_active = 1 " +
                        "AND (start_date IS NULL OR start_date <= NOW()) " +
                        "AND (end_date IS NULL OR end_date >= NOW()) " +
                        "ORDER BY display_order, created_at DESC";
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> banners = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> banner = new HashMap<>();
                banner.put("banner_id", rs.getInt("banner_id"));
                banner.put("title", getStringSafe(rs, "title"));
                banner.put("image_url", getStringSafe(rs, "image_url"));
                banner.put("link_url", getStringSafe(rs, "link_url"));
                banner.put("position", getStringSafe(rs, "position"));
                banner.put("display_order", rs.getInt("display_order"));
                banner.put("is_active", rs.getBoolean("is_active"));
                
                Timestamp startDate = rs.getTimestamp("start_date");
                Timestamp endDate = rs.getTimestamp("end_date");
                Timestamp createdAt = rs.getTimestamp("created_at");
                
                banner.put("start_date", startDate != null ? startDate.toString() : null);
                banner.put("end_date", endDate != null ? endDate.toString() : null);
                banner.put("created_at", createdAt != null ? createdAt.toString() : null);
                
                banners.add(banner);
            }
            
            PrintWriter out = response.getWriter();
            out.print(gson.toJson(banners));
            out.flush();
            
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void getAllBanners(HttpServletRequest request, HttpServletResponse response) 
            throws IOException, SQLException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "SELECT banner_id, title, image_url, link_url, position, " +
                        "display_order, is_active, start_date, end_date, created_at " +
                        "FROM banners " +
                        "ORDER BY display_order, created_at DESC";
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> banners = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> banner = new HashMap<>();
                banner.put("banner_id", rs.getInt("banner_id"));
                banner.put("title", getStringSafe(rs, "title"));
                banner.put("image_url", getStringSafe(rs, "image_url"));
                banner.put("link_url", getStringSafe(rs, "link_url"));
                banner.put("position", getStringSafe(rs, "position"));
                banner.put("display_order", rs.getInt("display_order"));
                banner.put("is_active", rs.getBoolean("is_active"));
                
                Timestamp startDate = rs.getTimestamp("start_date");
                Timestamp endDate = rs.getTimestamp("end_date");
                Timestamp createdAt = rs.getTimestamp("created_at");
                
                banner.put("start_date", startDate != null ? startDate.toString() : null);
                banner.put("end_date", endDate != null ? endDate.toString() : null);
                banner.put("created_at", createdAt != null ? createdAt.toString() : null);
                
                banners.add(banner);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("banners", banners);
            
            PrintWriter out = response.getWriter();
            out.print(gson.toJson(result));
            out.flush();
            
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void getBannersByPosition(HttpServletRequest request, HttpServletResponse response) 
            throws IOException, SQLException {
        String position = request.getParameter("position");
        if (position == null || position.isEmpty()) {
            sendError(response, "Position parameter is required");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "SELECT banner_id, title, image_url, link_url, position, " +
                        "display_order, is_active, start_date, end_date, created_at " +
                        "FROM banners " +
                        "WHERE position = ? AND is_active = 1 " +
                        "AND (start_date IS NULL OR start_date <= NOW()) " +
                        "AND (end_date IS NULL OR end_date >= NOW()) " +
                        "ORDER BY display_order, created_at DESC";
            
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, position);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> banners = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> banner = new HashMap<>();
                banner.put("banner_id", rs.getInt("banner_id"));
                banner.put("title", getStringSafe(rs, "title"));
                banner.put("image_url", getStringSafe(rs, "image_url"));
                banner.put("link_url", getStringSafe(rs, "link_url"));
                banner.put("position", getStringSafe(rs, "position"));
                banner.put("display_order", rs.getInt("display_order"));
                banner.put("is_active", rs.getBoolean("is_active"));
                
                Timestamp startDate = rs.getTimestamp("start_date");
                Timestamp endDate = rs.getTimestamp("end_date");
                Timestamp createdAt = rs.getTimestamp("created_at");
                
                banner.put("start_date", startDate != null ? startDate.toString() : null);
                banner.put("end_date", endDate != null ? endDate.toString() : null);
                banner.put("created_at", createdAt != null ? createdAt.toString() : null);
                
                banners.add(banner);
            }
            
            PrintWriter out = response.getWriter();
            out.print(gson.toJson(banners));
            out.flush();
            
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private String getStringSafe(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value != null ? value : "";
    }
    
    private void getBannerById(HttpServletRequest request, HttpServletResponse response) 
            throws IOException, SQLException {
        String bannerIdStr = request.getParameter("banner_id");
        if (bannerIdStr == null || bannerIdStr.isEmpty()) {
            sendError(response, "Banner ID is required");
            return;
        }
        
        int bannerId;
        try {
            bannerId = Integer.parseInt(bannerIdStr);
        } catch (NumberFormatException e) {
            sendError(response, "Invalid banner ID");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "SELECT banner_id, title, image_url, link_url, position, " +
                        "display_order, is_active, start_date, end_date, created_at " +
                        "FROM banners WHERE banner_id = ?";
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, bannerId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> banner = new HashMap<>();
                banner.put("banner_id", rs.getInt("banner_id"));
                banner.put("title", getStringSafe(rs, "title"));
                banner.put("image_url", getStringSafe(rs, "image_url"));
                banner.put("link_url", getStringSafe(rs, "link_url"));
                banner.put("position", getStringSafe(rs, "position"));
                banner.put("display_order", rs.getInt("display_order"));
                banner.put("is_active", rs.getBoolean("is_active"));
                
                Timestamp startDate = rs.getTimestamp("start_date");
                Timestamp endDate = rs.getTimestamp("end_date");
                Timestamp createdAt = rs.getTimestamp("created_at");
                
                banner.put("start_date", startDate != null ? startDate.toString() : null);
                banner.put("end_date", endDate != null ? endDate.toString() : null);
                banner.put("created_at", createdAt != null ? createdAt.toString() : null);
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("banner", banner);
                
                PrintWriter out = response.getWriter();
                out.print(gson.toJson(result));
                out.flush();
            } else {
                sendError(response, "Banner not found");
            }
            
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void createBanner(HttpServletRequest request, HttpServletResponse response) 
            throws IOException, SQLException {
        String title = request.getParameter("title");
        String imageUrl = request.getParameter("image_url");
        String linkUrl = request.getParameter("link_url");
        String position = request.getParameter("position");
        String displayOrderStr = request.getParameter("display_order");
        String isActiveStr = request.getParameter("is_active");
        String startDateStr = request.getParameter("start_date");
        String endDateStr = request.getParameter("end_date");
        
        // Validation
        if (title == null || title.trim().isEmpty()) {
            sendError(response, "Title is required");
            return;
        }
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            sendError(response, "Image URL is required");
            return;
        }
        if (position == null || position.trim().isEmpty()) {
            position = "home_top";
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "INSERT INTO banners (title, image_url, link_url, position, display_order, " +
                        "is_active, start_date, end_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, title.trim());
            stmt.setString(2, imageUrl.trim());
            stmt.setString(3, linkUrl != null && !linkUrl.trim().isEmpty() ? linkUrl.trim() : null);
            stmt.setString(4, position);
            
            int displayOrder = 0;
            if (displayOrderStr != null && !displayOrderStr.trim().isEmpty()) {
                try {
                    displayOrder = Integer.parseInt(displayOrderStr);
                } catch (NumberFormatException e) {
                    displayOrder = 0;
                }
            }
            stmt.setInt(5, displayOrder);
            
            boolean isActive = true;
            if (isActiveStr != null && !isActiveStr.trim().isEmpty()) {
                isActive = "1".equals(isActiveStr) || "true".equalsIgnoreCase(isActiveStr);
            }
            stmt.setBoolean(6, isActive);
            
            if (startDateStr != null && !startDateStr.trim().isEmpty()) {
                try {
                    stmt.setTimestamp(7, Timestamp.valueOf(startDateStr.replace("T", " ").replace("Z", "")));
                } catch (IllegalArgumentException e) {
                    stmt.setTimestamp(7, null);
                }
            } else {
                stmt.setTimestamp(7, null);
            }
            
            if (endDateStr != null && !endDateStr.trim().isEmpty()) {
                try {
                    stmt.setTimestamp(8, Timestamp.valueOf(endDateStr.replace("T", " ").replace("Z", "")));
                } catch (IllegalArgumentException e) {
                    stmt.setTimestamp(8, null);
                }
            } else {
                stmt.setTimestamp(8, null);
            }
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Banner created successfully");
                
                PrintWriter out = response.getWriter();
                out.print(gson.toJson(result));
                out.flush();
            } else {
                sendError(response, "Failed to create banner");
            }
            
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void updateBanner(HttpServletRequest request, HttpServletResponse response) 
            throws IOException, SQLException {
        String bannerIdStr = request.getParameter("banner_id");
        if (bannerIdStr == null || bannerIdStr.isEmpty()) {
            sendError(response, "Banner ID is required");
            return;
        }
        
        int bannerId;
        try {
            bannerId = Integer.parseInt(bannerIdStr);
        } catch (NumberFormatException e) {
            sendError(response, "Invalid banner ID");
            return;
        }
        
        String title = request.getParameter("title");
        String imageUrl = request.getParameter("image_url");
        String linkUrl = request.getParameter("link_url");
        String position = request.getParameter("position");
        String displayOrderStr = request.getParameter("display_order");
        String isActiveStr = request.getParameter("is_active");
        String startDateStr = request.getParameter("start_date");
        String endDateStr = request.getParameter("end_date");
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "UPDATE banners SET title = ?, image_url = ?, link_url = ?, position = ?, " +
                        "display_order = ?, is_active = ?, start_date = ?, end_date = ? " +
                        "WHERE banner_id = ?";
            
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, title != null ? title.trim() : "");
            stmt.setString(2, imageUrl != null ? imageUrl.trim() : "");
            stmt.setString(3, linkUrl != null && !linkUrl.trim().isEmpty() ? linkUrl.trim() : null);
            stmt.setString(4, position != null ? position : "home_top");
            
            int displayOrder = 0;
            if (displayOrderStr != null && !displayOrderStr.trim().isEmpty()) {
                try {
                    displayOrder = Integer.parseInt(displayOrderStr);
                } catch (NumberFormatException e) {
                    displayOrder = 0;
                }
            }
            stmt.setInt(5, displayOrder);
            
            boolean isActive = true;
            if (isActiveStr != null && !isActiveStr.trim().isEmpty()) {
                isActive = "1".equals(isActiveStr) || "true".equalsIgnoreCase(isActiveStr);
            }
            stmt.setBoolean(6, isActive);
            
            if (startDateStr != null && !startDateStr.trim().isEmpty()) {
                try {
                    stmt.setTimestamp(7, Timestamp.valueOf(startDateStr.replace("T", " ").replace("Z", "")));
                } catch (IllegalArgumentException e) {
                    stmt.setTimestamp(7, null);
                }
            } else {
                stmt.setTimestamp(7, null);
            }
            
            if (endDateStr != null && !endDateStr.trim().isEmpty()) {
                try {
                    stmt.setTimestamp(8, Timestamp.valueOf(endDateStr.replace("T", " ").replace("Z", "")));
                } catch (IllegalArgumentException e) {
                    stmt.setTimestamp(8, null);
                }
            } else {
                stmt.setTimestamp(8, null);
            }
            
            stmt.setInt(9, bannerId);
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Banner updated successfully");
                
                PrintWriter out = response.getWriter();
                out.print(gson.toJson(result));
                out.flush();
            } else {
                sendError(response, "Banner not found or no changes made");
            }
            
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    private void deleteBanner(HttpServletRequest request, HttpServletResponse response) 
            throws IOException, SQLException {
        String bannerIdStr = request.getParameter("banner_id");
        if (bannerIdStr == null || bannerIdStr.isEmpty()) {
            sendError(response, "Banner ID is required");
            return;
        }
        
        int bannerId;
        try {
            bannerId = Integer.parseInt(bannerIdStr);
        } catch (NumberFormatException e) {
            sendError(response, "Invalid banner ID");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "DELETE FROM banners WHERE banner_id = ?";
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, bannerId);
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Banner deleted successfully");
                
                PrintWriter out = response.getWriter();
                out.print(gson.toJson(result));
                out.flush();
            } else {
                sendError(response, "Banner not found");
            }
            
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("success", false);
        
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(error));
        out.flush();
    }
}

