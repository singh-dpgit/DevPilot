package devPilot.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@Configuration 
public class CryptoConfig {

    @Bean
    TextEncryptor tokenEncryptor(
        @Value("${app.token-encryptor-password}")String password,
        @Value("${app.token-encryptor-salt}") String salt){
            // Spring Security expects an 8-byte salt represented by 16 hexadecimal characters.
            if (salt == null || !salt.matches("(?i)[0-9a-f]{16}")) {
                throw new IllegalArgumentException(
                    "app.token-encryptor-salt must be exactly 16 hexadecimal characters");
            }
            return Encryptors.text(password, salt);
        }
    
}
