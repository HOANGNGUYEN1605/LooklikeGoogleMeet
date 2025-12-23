import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class TestHashPassword {
    public static void main(String[] args) {
        String password = "123123";
        String storedHash = "96cae35ce8a9b0244178bf28e4966c2ce1b8385723a96a6b838858cdd6ca0a1e";
        
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            String computedHash = hexString.toString();
            
            System.out.println("Password: " + password);
            System.out.println("Computed hash: " + computedHash);
            System.out.println("Stored hash:   " + storedHash);
            System.out.println("Match: " + computedHash.equals(storedHash));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

