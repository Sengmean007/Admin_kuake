package org.sengmean.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * The Swagger UI application for this test project.
 *
 *     mvn spring-boot:run
 *     http://localhost:8081/swagger-ui.html
 *
 * It exists to document APIs, not to be part of the Playwright suites - nothing under
 * {@code page} or {@code tests} depends on it, and it is not started by {@code mvn test}.
 *
 * Every request needs HTTP Basic credentials, so the browser prompts once before the UI
 * loads; see {@link ApiDocsSecurityConfig} for the filter chain and application.properties
 * for the account.
 *
 * One definition is published: "Test suite API", this application's own endpoints,
 * generated from the annotated controllers in this package. A hand-written spec for the
 * admin back office under test can be added beside it later - drop the .yaml into
 * src/main/resources/static/openapi/ and name it in application.properties, the way the
 * sibling NewWeibo_automation project serves its member API spec.
 *
 * The component scan is rooted at this package deliberately, and that matters more since the
 * page objects moved under the group id. {@code org.sengmean.page} is now a sibling of this
 * package rather than a top-level one, so it stays outside the scan - but moving this class
 * up to {@code org.sengmean}, or adding a {@code scanBasePackages} that reaches that far,
 * would start instantiating every page object as a Spring bean. They take a Playwright
 * {@code Page} in their constructors and have no business in an application context.
 */
@SpringBootApplication
public class ApiDocsApplication {

    /** Key of the HTTP Basic scheme in the generated definition; see ApiDocsSecurityConfig. */
    private static final String BASIC_AUTH = "basicAuth";

    public static void main(String[] args) {
        SpringApplication.run(ApiDocsApplication.class, args);
    }

    /** Describes this application's own generated definition, the first entry in the picker. */
    @Bean
    public OpenAPI testSuiteOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Admin_weibo test suite API")
                        .version("1.0-SNAPSHOT")
                        .description("""
                                Endpoints exposed by the test project itself, describing how the \
                                Playwright suites are configured and which safety tags gate them. \
                                There is no spec for the system under test in this project yet.""")
                        .license(new License().name("Internal QA use")))
                // Declaring the scheme is what puts the "Authorize" button in Swagger UI and
                // makes "Try it out" send the header. The requirement is set at the root so it
                // covers every operation without annotating each one. It documents the app's
                // own endpoints only - a spec for the system under test would describe a
                // different system and carry its own auth.
                .components(new Components().addSecuritySchemes(BASIC_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("""
                                        The docs app credentials, api.docs.user and \
                                        api.docs.password. The browser prompts for them on the \
                                        first request as well, so a session that answered that \
                                        prompt needs no second sign-in here.""")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }
}
