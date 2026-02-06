package com.pm.patientservice.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility class for bcrypt password operations.
 * Bcrypt is a one-way hashing algorithm - passwords cannot be decoded/decrypted.
 */
public class PasswordUtil {

    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    /**
     * Verifies if a plain text password matches the bcrypt hash.
     *
     * @param plainPassword The plain text password to verify
     * @param hashedPassword The bcrypt hash to verify against
     * @return true if the password matches, false otherwise
     *
     * Example:
     * String hash = "$2b$10$kwuZeno4vAtvhhaBRwRyf.tEF0dM2GRqJbMpT7XMn/ICX8Cl/wPLO";
     * boolean isValid = PasswordUtil.verifyPassword("myPassword123", hash);
     */
    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }
        return passwordEncoder.matches(plainPassword, hashedPassword);
    }

    /**
     * Generates a new bcrypt hash for a plain text password with salt rounds of 10.
     *
     * @param plainPassword The plain text password to hash
     * @return The bcrypt hash
     *
     * Example:
     * String hash = PasswordUtil.hashPassword("myPassword123");
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        return passwordEncoder.encode(plainPassword);
    }

    /**
     * Generates a new bcrypt hash with a custom number of salt rounds.
     *
     * @param plainPassword The plain text password to hash
     * @param strength The number of salt rounds (4-31, default is 10)
     * @return The bcrypt hash
     *
     * Example:
     * String hash = PasswordUtil.hashPassword("myPassword123", 12);
     */
    public static String hashPassword(String plainPassword, int strength) {
        if (plainPassword == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(strength);
        return encoder.encode(plainPassword);
    }

    /**
     * Example usage demonstrating verification against your hash.
     */
    public static void main(String[] args) {
        String yourHash = "$2b$10$kwuZeno4vAtvhhaBRwRyf.tEF0dM2GRqJbMpT7XMn/ICX8Cl/wPLO";
        
        // To find the original password, you need to try different values
        // Example: Test if a specific password matches the hash
        String testPassword = "yourPasswordHere";
        boolean matches = verifyPassword(testPassword, yourHash);
        System.out.println("Password matches: " + matches);
        
        // Generate a new hash
        String newHash = hashPassword("newPassword123");
        System.out.println("New hash: " + newHash);
        
        // Verify the new hash
        boolean newMatches = verifyPassword("newPassword123", newHash);
        System.out.println("New password verified: " + newMatches);
    }
}
