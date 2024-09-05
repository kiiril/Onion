package com.github.kiiril;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;

public class AES {
    // Generate a 256-bit AES key from a shared secret
    public static SecretKey generateKey(BigInteger sharedSecret) {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
            byte[] key = sha256.digest(sharedSecret.toByteArray());
            return new SecretKeySpec(Arrays.copyOf(key, 32), "AES");
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Cannot find SHA-256 algorithm" + e);
        }
        return null;
    }

    // Encrypt a message using AES in CBC mode
    public static String encrypt(String plainText, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Concatenate IV and encrypted message
            byte[] encryptedWithIv = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
            System.arraycopy(encrypted, 0, encryptedWithIv, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(encryptedWithIv);
        } catch (Exception e) {
            System.out.println("Cannot encrypt the message" + e);
        }
        return null;
    }

    // Decrypt a message using AES in CBC mode
    public static String decrypt(String encryptedText, SecretKey key) {
        try {
            // Separate IV and encrypted message
            byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedText);
            byte[] iv = Arrays.copyOfRange(encryptedWithIv, 0, 16);
            byte[] encrypted = Arrays.copyOfRange(encryptedWithIv, 16, encryptedWithIv.length);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("Cannot decrypt the message" + e);
        }
        return null;
    }
}
