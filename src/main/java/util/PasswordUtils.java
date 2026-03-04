package util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Utility class for password hashing and verification using BCrypt.
 * Compatible with database schema: users.password_hash (VARCHAR(255))
 * 
 * BCrypt hashes are stored in format: $2a$12$... (60 characters)
 * This matches the database schema requirements.
 */
public class PasswordUtils {
    
    /**
     * BCrypt cost factor (rounds). Higher value = more secure but slower.
     * Value 12 is a good balance between security and performance.
     */
    private static final int BCRYPT_ROUNDS = 12;
    
    /**
     * Minimum password length requirement
     */
    private static final int MIN_PASSWORD_LENGTH = 6;
    
    /**
     * Maximum password length to prevent DoS attacks
     */
    private static final int MAX_PASSWORD_LENGTH = 128;
    
    /**
     * Hashes a plain text password using BCrypt.
     * Compatible with database column: password_hash VARCHAR(255)
     * 
     * @param plainPassword The plain text password to hash
     * @return BCrypt hash string (60 characters, starts with $2a$12$)
     * @throws IllegalArgumentException if password is null, empty, or invalid
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        
        if (plainPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        
        if (plainPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Password must be at least %d characters long", MIN_PASSWORD_LENGTH)
            );
        }
        
        if (plainPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Password must be at most %d characters long", MAX_PASSWORD_LENGTH)
            );
        }
        
        try {
            // Generate BCrypt hash with cost factor 12
            // Format: $2a$12$[22 char salt][31 char hash] = 60 characters total
            return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_ROUNDS));
        } catch (Exception e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }
    
    /**
     * Verifies a plain text password against a BCrypt hash.
     * Used for login and password change verification.
     * 
     * @param plainPassword The plain text password to verify
     * @param hashedPassword The BCrypt hash from database (users.password_hash)
     * @return true if password matches, false otherwise
     * @throws IllegalArgumentException if either parameter is null or empty
     */
    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || plainPassword.trim().isEmpty()) {
            return false;
        }
        
        if (hashedPassword == null || hashedPassword.trim().isEmpty()) {
            return false;
        }
        
        // Validate BCrypt hash format (should start with $2a$, $2b$, or $2y$)
        if (!hashedPassword.startsWith("$2a$") && 
            !hashedPassword.startsWith("$2b$") && 
            !hashedPassword.startsWith("$2y$")) {
            return false;
        }
        
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (Exception e) {
            // Log error but don't expose details to prevent information leakage
            System.err.println("Error verifying password: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Validates password strength requirements.
     * 
     * @param password The password to validate
     * @return true if password meets requirements, false otherwise
     */
    public static boolean isValidPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            return false;
        }
        
        return password.length() >= MIN_PASSWORD_LENGTH && 
               password.length() <= MAX_PASSWORD_LENGTH;
    }
    
    /**
     * Checks if a string is a valid BCrypt hash format.
     * 
     * @param hash The hash string to validate
     * @return true if format is valid BCrypt hash
     */
    public static boolean isValidHash(String hash) {
        if (hash == null || hash.trim().isEmpty()) {
            return false;
        }
        
        // BCrypt hashes are exactly 60 characters and start with $2a$, $2b$, or $2y$
        return hash.length() == 60 && 
               (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"));
    }
}
