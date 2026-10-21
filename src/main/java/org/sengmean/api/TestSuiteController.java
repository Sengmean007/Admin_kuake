package org.sengmean.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only endpoints describing how the Playwright suites are configured.
 *
 * This is what keeps the generated definition from being an empty page, and it doubles as
 * a live answer to the question the tags raise most often: which suites exist, and which
 * of them are unsafe to run unattended.
 *
 * Nothing here touches a browser or the system under test.
 */
@RestController
@RequestMapping("/api/test-suite")
@Tag(name = "Test suite", description = "How the Playwright suites are configured")
public class TestSuiteController {

    /** Mirrors the defaults in BaseTest, which Config/config.properties supplies and -D overrides. */
    @GetMapping("/environment")
    @Operation(summary = "The environment the suites run against",
            description = "The resolved base URL and credentials keys used by BaseTest. "
                    + "Values come from system properties, so this reflects how the JVM "
                    + "serving these docs was started, not necessarily a test run.")
    @ApiResponse(responseCode = "200", description = "The resolved environment settings")
    public Environment environment() {
        // An origin with no path and no query - the same shape BaseTest defaults to. Paths are
        // appended to it, so a query string here would swallow them; base.query is the
        // supported way to pass one.
        //
        // The fallbacks are empty rather than a literal address, so a JVM started without
        // Config/ says "nothing configured" instead of naming an environment it is not on.
        return new Environment(
                System.getProperty("base.url", ""),
                System.getProperty("test.username", ""),
                System.getProperty("test.captcha", "1111"),
                Boolean.parseBoolean(System.getProperty("headless", "true")));
    }

    @GetMapping("/suites")
    @Operation(summary = "The suites and what they cover")
    public List<Suite> suites() {
        // Empty until the first admin suite exists. Add a row when one does: this endpoint is
        // the live answer to "which suites are there, and which are unsafe to run unattended",
        // and a list that has quietly fallen behind the source is worse than no list at all.
        return List.of();
    }

    @GetMapping("/tags")
    @Operation(summary = "The safety tags and how to exclude them",
            description = "Both tags should stay excluded on an ordinary run: "
                    + "mvn test -DexcludedGroups=known-defect,creates-data")
    public List<TagInfo> tags() {
        return List.of(
                new TagInfo("known-defect",
                        "Asserts the behaviour the application should have, so it fails "
                                + "today. The regression net for open findings.",
                        false),
                new TagInfo("creates-data",
                        "Has side effects that outlive the run - creates or edits a record "
                                + "in the back office, approves a transaction, or changes a "
                                + "setting other suites depend on.",
                        true));
    }

    /** The resolved test environment settings. */
    public record Environment(String baseUrl, String username, String captcha,
                              boolean headless) {
    }

    /** One test class and the tags it carries. */
    public record Suite(String className, String covers, boolean hasKnownDefects,
                        boolean hasCreatesData) {
    }

    /** One safety tag. */
    public record TagInfo(String name, String meaning, boolean hasSideEffects) {
    }
}
