import javax.crypto.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

public class RSA {
    private PublicKey publicKey;
    private PrivateKey privateKey;
    private Cipher cipher;

    public RSA() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();

            this.publicKey = keyPair.getPublic();
            this.privateKey = keyPair.getPrivate();
            this.cipher = Cipher.getInstance("RSA/ECB/NoPadding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            System.out.println("Something wrong with initializing RSA...");
        }
    }

    public String encrypt(String message) {
        byte[] byteMessage = message.getBytes(StandardCharsets.UTF_8);
        try {
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedMessage = cipher.doFinal(byteMessage);
            return encode(encryptedMessage);
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            System.out.println("Something wrong in encrypt()...");
            throw new RuntimeException(e);
        }
    }

    public String decrypt(String message) {
        byte[] byteMessage = decode(message);
        try {
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedMessage = cipher.doFinal(byteMessage);
            return new String(decryptedMessage);
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            System.out.println("Something wrong in decrypt()...");
            throw new RuntimeException(e);
        }
    }

    private byte[] decode(String data) {
        return Base64.getDecoder().decode(data);
    }

    private String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static void main(String[] args) {
        RSA rsa = new RSA();
        String message = "I am an Iron Man";
        String cipherText = rsa.encrypt(message);
        System.out.println("Cipher text is: " + cipherText);
        String plainText = rsa.decrypt(cipherText).trim();
        System.out.println("Plain text is: " + plainText);
    }
}
