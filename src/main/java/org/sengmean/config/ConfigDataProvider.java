package org.sengmean.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Every setting a run can be pointed at, read from {@code Config/} rather than from the POM.
 *
 * <h2>Why the settings moved out of the POM</h2>
 * The environment, the account and the captcha used to be literals inside Surefire's
 * {@code <systemPropertyVariables>}. That reads like a default and is not one: Surefire plants
 * those values into the forked JVM after Maven has parsed the command line, so the literal wins
 * and {@code ./mvnw test -Dtest.username=someone} silently ran as {@code qa000092} anyway. There
 * was no way to point a run at another account without editing the build file, which is the one
 * file a run should never have to touch.
 *
 * <p>They live in {@code Config/config.properties} now, which is a plain text file a tester can
 * read and change without knowing Maven, and which the sibling UMPay suite uses for the same job
 * and in the same shape. Anything in it can still be overridden for one run from the command
 * line, and that override now actually takes effect.
 *
 * <h2>Where a value comes from, in order</h2>
 * <ol>
 *   <li>{@code -Dkey=value} on the command line - one run, or CI</li>
 *   <li>the matching environment variable - {@code base.url} reads {@code BASE_URL}, so a CI
 *       job can set credentials without putting them in a command line that gets logged</li>
 *   <li>{@code Config/secrets.properties} - not committed; see the {@code .example} beside it</li>
 *   <li>{@code Config/config.properties} - committed, and the answer for almost everything</li>
 *   <li>the fallback the caller passes, which is the last resort rather than the usual case</li>
 * </ol>
 *
 * <h2>It seeds the system properties as well as answering questions</h2>
 * Loading this class copies every key it found into {@link System#getProperties()}, skipping any
 * key that is already set so a {@code -D} is never overwritten. That is what lets the rest of the
 * project go on reading {@code System.getProperty} without being rewritten - and, more usefully,
 * it is what puts a value set in {@code Config/} into the environment panel of the HTML report.
 * A run whose target came from a file and a run whose target came from {@code -D} now describe
 * themselves identically, which is the whole point of that panel.
 *
 * <h2>A missing Config directory is not an error</h2>
 * The suites have to keep working from a checkout that has not been configured, from a CI job
 * that passes everything on the command line, and from an IDE run configuration started in some
 * other directory. So a file that is not there is simply skipped and the fallbacks apply, which
 * is the behaviour the project had before this class existed.
 */
public final class ConfigDataProvider {

    /**
     * Where the two files live, relative to wherever the run was started from.
     *
     * <p>Overridable with {@code -Dconfig.dir=...} for the one case that needs it: a run launched
     * from somewhere other than the project directory, such as an IDE configuration with a
     * different working directory. The report generators resolve their own paths exactly this
     * way, so there is one convention here rather than two.
     */
    private static final Path DIR = Path.of(System.getProperty("config.dir", "Config"));

    private static final String CONFIG_FILE = "config.properties";

    /** Not committed. Holds the mail password and anything else that must not be in git. */
    private static final String SECRETS_FILE = "secrets.properties";

    /** Everything both files offered, secrets last so they win over the committed defaults. */
    private static final Map<String, String> VALUES = load();

    static {
        seedSystemProperties();
    }

    private ConfigDataProvider() {
    }

    /**
     * The value for {@code key}, or {@code fallback} when nothing supplies one.
     *
     * <p>A blank answer counts as no answer at every level. That matters more than it looks:
     * Surefire plants {@code ${case}} into the fork as an empty string when the POM property is
     * undefined, so a caller asking for an unset key gets {@code ""} rather than {@code null},
     * and code that only guards against null goes on to parse the empty string.
     */
    public static String get(String key, String fallback) {
        String property = System.getProperty(key);
        if (isSet(property)) {
            return property.trim();
        }
        String environment = System.getenv(environmentName(key));
        if (isSet(environment)) {
            return environment.trim();
        }
        String configured = VALUES.get(key);
        return isSet(configured) ? configured.trim() : fallback;
    }

    /** The value for {@code key}, or an empty string. */
    public static String get(String key) {
        return get(key, "");
    }

    /** {@code true} only for the word "true", so an absent or misspelt setting means false. */
    public static boolean flag(String key, boolean fallback) {
        return Boolean.parseBoolean(get(key, String.valueOf(fallback)));
    }

    /**
     * A number setting, falling back rather than throwing when the value is not one.
     *
     * <p>Throwing is what the project did before, and it did it from inside a static initializer
     * in {@code BaseTest} - which fails every case in every suite with a {@code NoClassDefFound}
     * for the base class, an error that says nothing whatsoever about a mistyped number in a
     * properties file. A bad value is reported and ignored instead.
     */
    public static double number(String key, double fallback) {
        String value = get(key, "");
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException notANumber) {
            System.out.println("Config: " + key + " is not a number (" + value
                    + "), using " + fallback);
            return fallback;
        }
    }

    /** Every key that was configured, in the order the files listed them. For diagnostics. */
    public static Map<String, String> all() {
        return Map.copyOf(VALUES);
    }

    /**
     * {@code base.url} becomes {@code BASE_URL}.
     *
     * <p>Dots are not legal in an environment variable name on any shell worth supporting, so the
     * convention has to be stated somewhere; it is stated here and repeated in the comments of
     * {@code config.properties} so nobody has to read Java to find out what to export.
     */
    private static String environmentName(String key) {
        return key.toUpperCase().replace('.', '_').replace('-', '_');
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private static Map<String, String> load() {
        Map<String, String> values = new LinkedHashMap<>();
        readInto(values, DIR.resolve(CONFIG_FILE));
        readInto(values, DIR.resolve(SECRETS_FILE));
        return values;
    }

    private static void readInto(Map<String, String> values, Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            // Read as UTF-8 rather than through the default load(InputStream), which is
            // ISO-8859-1 and would mangle any non-ASCII in a message the suite compares against.
            properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException cannotRead) {
            // Configuration that cannot be read is worth saying out loud, but it is not worth
            // refusing to run over: every value has a fallback and the run will say which
            // environment it went to in the report either way.
            System.out.println("Config: could not read " + file + " (" + cannotRead.getMessage()
                    + "), carrying on with the defaults");
            return;
        }
        for (String name : properties.stringPropertyNames()) {
            values.put(name, properties.getProperty(name));
        }
    }

    /**
     * Copies the configured values into the system properties, without displacing a {@code -D}.
     *
     * <p>The skip list is what keeps precedence honest. Anything already set arrived either from
     * the command line or from Surefire's own {@code <systemPropertyVariables>}, and in both
     * cases it was asked for more specifically than a line in a file.
     */
    private static void seedSystemProperties() {
        List<String> seeded = new ArrayList<>();
        for (Map.Entry<String, String> entry : VALUES.entrySet()) {
            String key = entry.getKey();
            if (isSet(System.getProperty(key)) || !isSet(entry.getValue())) {
                continue;
            }
            // An environment variable outranks the file, so it is what gets seeded when both
            // have something to say. Reading through get() rather than from the map keeps that
            // rule in one place instead of restating it here.
            System.setProperty(key, get(key, entry.getValue().trim()));
            seeded.add(key);
        }
        if (!seeded.isEmpty() && flag("config.verbose", false)) {
            System.out.println("Config: " + DIR + " supplied " + seeded);
        }
    }
}
