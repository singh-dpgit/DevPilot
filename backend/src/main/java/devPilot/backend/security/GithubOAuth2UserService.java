package devPilot.backend.security;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import devPilot.backend.entity.User;
import devPilot.backend.services.UserService;

@Service 
public class GithubOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    // GitHub se aaye user data ko hamare database mein save/update karne ki service.
    private final UserService userService;
    // Spring ka ready-made service access token ke saath GitHub se user profile mangata hai.
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

    public GithubOAuth2UserService(UserService userService) {
        this.userService = userService;
    }

    @Override 
    public OAuth2User loadUser(OAuth2UserRequest userRequest)throws OAuth2AuthenticationException {
        // GitHub profile fields fetch
        OAuth2User  githubUser = delegate.loadUser(userRequest);
        
        // GitHub token and scopes fetch
        String accessToken = userRequest.getAccessToken().getTokenValue();
        String scopes=userRequest.getAccessToken().getScopes()!=null?String.join(",", userRequest.getAccessToken().getScopes()):"read:user,repo";
        
        // create login principal with user data and access token
        User user=userService.upsertFromGitHub(githubUser.getAttributes(), accessToken, scopes);
        return new AppUserPrincipal(user, githubUser.getAttributes());
    }
    
    
}
