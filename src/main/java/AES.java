import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;

public class AES {
    public static final byte[] KEY = new byte[] {82, 20, 102, -119, -111, 58, 14, -22, 117, 29, -105, -36, 120, -34, 73, 28};
    public static final byte[] IV = new byte[] {60, -27, -68, -46, -66, 112, -101, 126, -76, 16, -81, -89, 11, 37, 12, -91};
    public static final SecretKeySpec symmetricKey = new SecretKeySpec(KEY, "AES");;
    public static final IvParameterSpec iv = new IvParameterSpec(IV);
    public static String encrypt(String input, SecretKey key, IvParameterSpec iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);
            return encode(cipher.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (InvalidAlgorithmParameterException | NoSuchPaddingException | IllegalBlockSizeException |
                 NoSuchAlgorithmException | BadPaddingException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public static String decrypt(String stringCipherText, SecretKey key, IvParameterSpec iv) throws Exception {
        byte[] cipherText = decode(stringCipherText);
        Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, iv);
        byte[] plainText = cipher.doFinal(cipherText);
        return new String(plainText);
    }

    public static IvParameterSpec generateIv() {
        byte[] initializationVector = new byte[16];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(initializationVector);
        return new IvParameterSpec(initializationVector);
    }

    public static SecretKey generateKey() throws NoSuchAlgorithmException {
        KeyGenerator keygenerator = KeyGenerator.getInstance("AES");
        keygenerator.init(128);
        return keygenerator.generateKey();
    }

    private static byte[] decode(String data) {
        return Base64.getDecoder().decode(data);
    }

    private static String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    // test client
    public static void main(String[] args) throws Exception {
        // Takes input from the keyboard
        Scanner message = new Scanner(System.in);
        String plainText = message.nextLine();
        message.close();

        // Encrypt the message using the symmetric key
        // Hardcode keys for testing
        String cipherText = encrypt(plainText, symmetricKey, iv);

        System.out.println("The encrypted message is: " + cipherText);

        // Decrypt the encrypted message
        String decryptedText = decrypt(cipherText, symmetricKey, iv);

        System.out.println( "Your original message is: " + decryptedText);
    }
}
