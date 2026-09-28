package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class ProductImages {

    private ProductImages() {
    }

    /** Image URLs of a product, primary image first. Blank URLs are skipped. */
    public static List<String> urls(Connection conn, int productId) throws SQLException {
        String sql = "SELECT image_url FROM product_images WHERE product_id = ? "
                + "ORDER BY is_primary DESC, image_order, image_id";
        List<String> urls = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String url = rs.getString(1);
                    if (url != null && !url.isBlank()) {
                        urls.add(url);
                    }
                }
            }
        }
        return urls;
    }
}
