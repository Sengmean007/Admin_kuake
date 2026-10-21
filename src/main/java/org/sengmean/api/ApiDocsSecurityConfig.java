package org.sengmean.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Puts HTTP Basic in front of the Swagger UI app.
 *
 * Everything is protected - Swagger UI itself, springdoc's generated /v3/api-docs, the
 * hand-written member spec under /openapi/ and the /api/test-suite endpoints - so the
 * browser asks for credentials once, on the first request, and the definitions then load
 * with the same cached credentials. The alternative, leaving the UI open and guarding only
 * /api/**, publishes the environment endpoint's contents in the docs page anyway.
 *
 * The account is Spring Boot's single in-memory user, configured in application.properties
 * from {@code api.docs.user} and {@code api.docs.password}. These are local development
 * credentials for a documentation app on localhost, not a secret store; override them per
 * run rather than editing the defaults (see the properties file for how).
 *
 * This affects the Swagger UI app only. {@code mvn test} never starts a Spring context, so
 * the Playwright suites neither see this filter chain nor need credentials.
 */
@Configuration
public class ApiDocsSecurityConfig {

    @Bean
    public SecurityFilterChain apiDocsSecurity(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                // No session to protect: every request carries its own credentials, which
                // is also what lets Swagger UI's "Try it out" work with only the Authorize
                // header set. Without disabling CSRF a POST from the UI would be rejected
                // before it reached a controller.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }
}
