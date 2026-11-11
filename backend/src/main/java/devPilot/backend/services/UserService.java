package devPilot.backend.services;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import devPilot.backend.entity.User;
import devPilot.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor 
public class UserService {
    private final UserRepository userRepository;
    private final TextEncryptor tokenEncryptor;

    @Transactional
    public User upsertFromGitHub(Map<String,Object> attributes, String accessToken,
            String scopes) {
        // GitHub profile se user ki pehchan aur dikhane layak details nikaalo.
        Long githubId=toLong(attributes.get("id"));
        String login=String.valueOf(attributes.get("login"));
        String name=attributes.get("name")!=null
        ?String.valueOf(attributes.get("name"))
        :login;

        String avatarUrl=attributes.get("avatar_url")!=null
        ?String.valueOf(attributes.get("avatar_url"))
        :null;

        // Token ko database mein seedha rakhne ke bajay pehle encrypt karo.
        String encryptedToken = tokenEncryptor.encrypt(accessToken);

        // GitHub ID pehle se ho to us user ko update karo, warna naya user banao.
        User user=userRepository.findByGithubId(githubId).orElseGet(User::new);
        user.setGithubId(githubId);
        user.setGithubUsername(login);
        user.setDisplayName(name);
        user.setAvatarUrl(avatarUrl);
        user.setAccessToken(encryptedToken);
        user.setTokenScope(scopes);
        // Naya/updated user database mein save karke wapas do.
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User requiredById(UUID id)
    {
        // App ke internal user ID se record lo; na mile to error do.
        return userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
    }

    public String decryptAccessToken(User user) {
        // GitHub API ko token chahiye ho to stored encrypted value decrypt karo.
        return tokenEncryptor.decrypt(user.getAccessToken());
    }
    private Long toLong(Object value) {
    if (value instanceof Number number) {
        return number.longValue();
    }
    if (value instanceof String text) {
        return Long.valueOf(text);
    }
    throw new IllegalArgumentException("GitHub user ID is missing or invalid");
}

    
    
}
