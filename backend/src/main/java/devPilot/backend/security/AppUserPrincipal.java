package devPilot.backend.security;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.user.OAuth2User;

import devPilot.backend.entity.User;

public class AppUserPrincipal implements OAuth2User {
    // Database user and github profile fields encapsulated in this class
    private final User user;
    private final Map<String, Object> attributes;

    public AppUserPrincipal(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }

    // database user UUID
    public UUID getId() {
        return user.getId();
    }

    // Database user access
    public User getUser() {
        return user;
    }

    @Override
    //github attributes to spring security 
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    //Normal user role to this login
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AuthorityUtils.createAuthorityList("ROLE_USER");
    }

    @Override
    // User name for spring security
    public String getName() {
        return String.valueOf(attributes.get("id"));
    }
    
}
