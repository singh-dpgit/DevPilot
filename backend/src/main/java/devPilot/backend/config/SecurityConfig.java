package devPilot.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import devPilot.backend.security.GithubOAuth2UserService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final GithubOAuth2UserService githubOAuth2UserService;

    public SecurityConfig(GithubOAuth2UserService githubOAuth2UserService) {
        this.githubOAuth2UserService = githubOAuth2UserService;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationSuccessHandler oauth2SuccessHandler,
            AuthenticationFailureHandler oauth2FailureHandler) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/favicon.ico",
                                "/css/**",
                                "/js/**",
                                "/api/auth/login-url",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo.userService(githubOAuth2UserService))
                        // Resolve the handler beans here to avoid constructor-injection cycles.
                        .successHandler(oauth2SuccessHandler)
                        .failureHandler(oauth2FailureHandler))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("DEVPILOT_SESSION"));
        return http.build();
    }

    @Bean
    AuthenticationSuccessHandler oAuth2SuccessHandler(
            @Value("${app.frontend-url}") String frontendUrl) {
        SimpleUrlAuthenticationSuccessHandler handler = new SimpleUrlAuthenticationSuccessHandler();
        String baseUrl = normalizeBaseUrl(frontendUrl);
        String callbackUrl = baseUrl + "/?oauth=callback";
        handler.setDefaultTargetUrl(callbackUrl);
        handler.setAlwaysUseDefaultTargetUrl(true);
        return handler;
    }

    @Bean
    AuthenticationFailureHandler oAuth2FailureHandler(
            @Value("${app.frontend-url}") String frontendUrl) {
        SimpleUrlAuthenticationFailureHandler handler = new SimpleUrlAuthenticationFailureHandler();
        String baseUrl = normalizeBaseUrl(frontendUrl);
        handler.setDefaultFailureUrl(baseUrl + "/?error=oauth_failed");
        return handler;
    }

    private String normalizeBaseUrl(String frontendUrl) {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return "http://localhost:8080";
        }
        String trimmed = frontendUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
