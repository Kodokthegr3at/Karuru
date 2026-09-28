package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Part;

import util.Json;
import util.Uploads;

/** New product listing from create-listing.jsp (multipart: form fields plus up to 10 "images"). */
@WebServlet("/CreateListingServlet")
@MultipartConfig(maxFileSize = Uploads.MAX_IMAGE_BYTES, maxRequestSize = 11 * Uploads.MAX_IMAGE_BYTES)
public class CreateListingServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;
    private static final int MAX_IMAGES = 10;
    private static final int MAX_SPECS = 50;

    public CreateListingServlet() {
        route("POST", "create", Access.USER, this::create);
    }

    /** The listing form after validation. Prices are null when not given. */
    private record Listing(String name, String description, int categoryId, BigDecimal price, int stock,
            String condition, boolean negotiable, boolean rental, BigDecimal rentalDaily, BigDecimal rentalWeekly,
            BigDecimal rentalMonthly, BigDecimal weightGrams) {
    }

    private void create(Call call) throws IOException, SQLException {
        if (!canSell(call.db(), call.userId())) {
            call.error(SC_FORBIDDEN,
                    "商品を出品するには、住所を登録するか、プロフィール情報（氏名・電話番号・メールアドレス）を完成させる必要があります");
            return;
        }
        Listing listing = readListing(call);
        if (listing == null) {
            return;
        }
        if (!categoryExists(call.db(), listing.categoryId())) {
            call.error(SC_BAD_REQUEST, "カテゴリが見つかりません");
            return;
        }

        // Store the images first: if one is not a real image the listing is rejected and nothing is written.
        List<String> images = new ArrayList<>();
        try {
            for (Part part : imageParts(call)) {
                images.add(Uploads.saveImage(part, "products"));
            }
        } catch (Uploads.RejectedUpload e) {
            deleteUploads(images);
            call.error(SC_BAD_REQUEST, e.getMessage());
            return;
        }

        Connection db = call.beginTransaction();
        String slug = slug(listing.name());
        int productId = insertProduct(db, call.userId(), listing, slug, images.isEmpty() ? null : images.get(0));
        try (PreparedStatement stmt = db.prepareStatement("INSERT INTO product_categories (product_id, category_id) VALUES (?, ?)")) {
            stmt.setInt(1, productId);
            stmt.setInt(2, listing.categoryId());
            stmt.executeUpdate();
        }
        try (PreparedStatement stmt = db.prepareStatement(
                "INSERT INTO product_images (product_id, image_url, image_order, is_primary) VALUES (?, ?, ?, ?)")) {
            for (int i = 0; i < images.size(); i++) {
                stmt.setInt(1, productId);
                stmt.setString(2, images.get(i));
                stmt.setInt(3, i);
                stmt.setBoolean(4, i == 0);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
        insertSpecifications(db, productId, call);
        db.commit();
        call.ok(Json.obj("success", true, "message", "商品が正常に登録されました", "product_id", productId, "slug", slug));
    }

    /** Validates the form; sends a 400 and returns null when something is missing or malformed. */
    private static Listing readListing(Call call) throws IOException {
        String name = call.param("product_name");
        String description = call.param("description");
        Integer categoryId = call.intParam("category_id");
        Integer stock = call.intParam("stock_quantity");
        BigDecimal price = decimal(call.param("price"));
        if (name == null || description == null || call.param("condition") == null
                || categoryId == null || stock == null || price == null) {
            call.error(SC_BAD_REQUEST, "必須フィールドが不足しています");
            return null;
        }
        if (price.signum() < 0 || stock < 1) {
            call.error(SC_BAD_REQUEST, "価格は0以上、在庫は1以上で入力してください");
            return null;
        }
        boolean rental = isOn(call.param("is_rental"));
        BigDecimal weight = decimal(call.param("weight"));
        if (weight != null && "kg".equals(call.param("weight_unit"))) {
            weight = weight.multiply(BigDecimal.valueOf(1000));
        }
        String daily = call.param("rental_price_daily") != null ? call.param("rental_price_daily") : call.param("rental_price");
        return new Listing(name, description, categoryId, price, stock, normalizeCondition(call.param("condition")),
                isOn(call.param("is_negotiable")), rental,
                rental ? decimal(daily) : null,
                rental ? decimal(call.param("rental_price_weekly")) : null,
                rental ? decimal(call.param("rental_price_monthly")) : null,
                weight);
    }

    /** A seller needs a shipping address, or a complete profile (name, phone and email). */
    private static boolean canSell(Connection db, int userId) throws SQLException {
        String sql = "SELECT (SELECT COUNT(*) FROM user_addresses a WHERE a.user_id = u.user_id) > 0 "
                + "OR (COALESCE(TRIM(u.full_name), '') <> '' AND COALESCE(TRIM(u.phone), '') <> '' AND COALESCE(TRIM(u.email), '') <> '') "
                + "FROM users u WHERE u.user_id = ?";
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static boolean categoryExists(Connection db, int categoryId) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement("SELECT 1 FROM categories WHERE category_id = ? AND is_active = 1")) {
            stmt.setInt(1, categoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int insertProduct(Connection db, int userId, Listing l, String slug, String mainImage) throws SQLException {
        String sql = "INSERT INTO products (user_id, product_name, slug, description, price, stock_quantity, `condition`, "
                + "is_rental, rental_price_daily, rental_price_weekly, rental_price_monthly, weight, is_negotiable, image_url, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'available')";
        try (PreparedStatement stmt = db.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, userId);
            stmt.setString(2, l.name());
            stmt.setString(3, slug);
            stmt.setString(4, l.description());
            stmt.setBigDecimal(5, l.price());
            stmt.setInt(6, l.stock());
            stmt.setString(7, l.condition());
            stmt.setBoolean(8, l.rental());
            setDecimal(stmt, 9, l.rentalDaily());
            setDecimal(stmt, 10, l.rentalWeekly());
            setDecimal(stmt, 11, l.rentalMonthly());
            setDecimal(stmt, 12, l.weightGrams());
            stmt.setBoolean(13, l.negotiable());
            stmt.setString(14, mainImage);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** Brand first, then the spec_name_N / spec_value_N pairs from the form, in form order. */
    private static void insertSpecifications(Connection db, int productId, Call call) throws SQLException {
        List<String[]> specs = new ArrayList<>();
        if (call.param("brand") != null) {
            specs.add(new String[] {"Brand", call.param("brand")});
        }
        for (int i = 0; i < MAX_SPECS; i++) {
            String name = call.param("spec_name_" + i);
            String value = call.param("spec_value_" + i);
            if (name != null && value != null) {
                specs.add(new String[] {name, value});
            }
        }
        String sql = "INSERT INTO product_specifications (product_id, spec_name, spec_value, display_order) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            for (int i = 0; i < specs.size(); i++) {
                stmt.setInt(1, productId);
                stmt.setString(2, specs.get(i)[0]);
                stmt.setString(3, specs.get(i)[1]);
                stmt.setInt(4, i);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private static List<Part> imageParts(Call call) throws IOException, Uploads.RejectedUpload {
        List<Part> images = new ArrayList<>();
        try {
            for (Part part : call.request.getParts()) {
                if ("images".equals(part.getName()) && part.getSize() > 0) {
                    images.add(part);
                }
            }
        } catch (ServletException | IllegalStateException e) {
            throw new Uploads.RejectedUpload("画像サイズは10MB以下にしてください");
        }
        if (images.size() > MAX_IMAGES) {
            throw new Uploads.RejectedUpload("画像は" + MAX_IMAGES + "枚までです");
        }
        return images;
    }

    private static void deleteUploads(List<String> urls) {
        for (String url : urls) {
            try {
                Files.deleteIfExists(Uploads.root().resolve(url.substring("uploads/".length())));
            } catch (IOException e) {
                // best effort: an orphaned file is harmless
            }
        }
    }

    /** Readable ASCII part of the name plus a random suffix (Japanese-only names become "product-xxxxxxxx"). */
    private static String slug(String name) {
        String ascii = name.toLowerCase().replaceAll("[^a-z0-9\\s-]", "").trim().replaceAll("[\\s-]+", "-");
        return (ascii.isEmpty() ? "product" : ascii) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String normalizeCondition(String condition) {
        return switch (condition.toLowerCase()) {
            case "new", "新品" -> "new";
            case "like-new", "like_new", "ほぼ新品" -> "like_new";
            case "acceptable", "fair", "可" -> "fair";
            case "poor", "悪い" -> "poor";
            default -> "good";
        };
    }

    private static boolean isOn(String value) {
        return "1".equals(value) || "true".equals(value) || "on".equals(value);
    }

    private static BigDecimal decimal(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void setDecimal(PreparedStatement stmt, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.DECIMAL);
        } else {
            stmt.setBigDecimal(index, value);
        }
    }
}
