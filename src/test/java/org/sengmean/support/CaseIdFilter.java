package org.sengmean.support;

import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.sengmean.report.CaseIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs a single case by its client case id: {@code ./mvnw test -Dcase=LGN-002}.
 *
 * <h2>Why a filter and not -Dtest</h2>
 * A case id lives in the {@code @DisplayName} - "LGN-002: an empty form cannot be submitted" -
 * and nowhere else. Surefire's {@code -Dtest} is a class and <b>method name</b> filter, so
 * {@code -Dtest=LGN-002} matches no method and reports the same misleading "Tests run: 0" that
 * the POM's {@code -Dsuite} comment describes. Selecting the case therefore means knowing which
 * Java method carries it, which is exactly the lookup a case id is supposed to save.
 *
 * The JUnit Platform applies a {@link PostDiscoveryFilter} after the engines have resolved the
 * tree, when display names exist, so the id can be matched directly. Registered through
 * {@code META-INF/services}, alongside the report listeners, so it works wherever the suites are
 * launched from - Maven, the IDE, a run configuration.
 *
 * <h2>Usage</h2>
 * <pre>
 *   ./mvnw test -Dcase=LGN-002                      one case, found wherever it lives
 *   ./mvnw test -Dcase=LGN-002,LGN-003              several at once
 *   ./mvnw test -Dsuite=AuthSuite -Dcase=LGN-002    narrow the scan first, for a faster run
 * </pre>
 *
 * With no {@code -Dcase} the filter includes everything, so ordinary runs are untouched.
 *
 * <h2>What counts as a match</h2>
 * The display name must <i>start</i> with the id, and the character after it must not be one that
 * could continue an id. That is what keeps {@code -Dcase=LGN-002} off a hypothetical LGN-0021:
 * a plain {@code startsWith} would quietly run both. Matching is case-insensitive so the ids can
 * be typed as they read in the workbook or in lower case.
 *
 * Every selected case is printed as it is included, because a filter that selects nothing
 * otherwise looks identical to a suite that passed.
 */
public class CaseIdFilter implements PostDiscoveryFilter {

    /** System property carrying the case ids, comma separated. */
    public static final String PROPERTY = "case";

    private final List<String> ids = read();

    /**
     * Case ids already announced. The filter is applied once per engine tree - the suite engine
     * resolves the same case the Jupiter engine does - so without this the selection is printed
     * twice for a suite run and reads as two cases.
     */
    private final Set<String> announced = ConcurrentHashMap.newKeySet();

    private static List<String> read() {
        String raw = System.getProperty(PROPERTY);
        List<String> parsed = new ArrayList<>();
        if (raw == null) {
            return parsed;
        }
        for (String part : raw.split(",")) {
            String id = part.trim();
            if (!id.isEmpty()) {
                parsed.add(id.toUpperCase(Locale.ROOT));
            }
        }
        return parsed;
    }

    @Override
    public FilterResult apply(TestDescriptor descriptor) {
        // Containers must survive, or the tests underneath them are never reached. The platform
        // prunes the ones that end up empty, so no class is opened for a case it does not carry.
        if (ids.isEmpty() || !descriptor.isTest()) {
            return FilterResult.included(null);
        }
        String displayName = descriptor.getDisplayName();
        for (String id : ids) {
            if (matches(displayName, id)) {
                if (announced.add(displayName)) {
                    System.out.println("[case] " + id + " -> " + displayName);
                }
                return FilterResult.included("matched -D" + PROPERTY + "=" + id);
            }
        }

        // Nothing in the display name. The id may still be one a case declares in an
        // "Also covers workbook rows:" comment, which is deliberately kept out of the display
        // name - see HtmlReportGenerator.Spec#alsoCovers - and which the client test case page
        // puts a Run button on. Without this second look those buttons run nothing at all and
        // report it as "Tests run: 0", which reads as a passing build.
        String selector = selectorOf(descriptor);
        if (selector != null) {
            for (String id : ids) {
                if (selector.equals(Aliases.SELECTORS.get(id))) {
                    if (announced.add(displayName)) {
                        System.out.println("[case] " + id + " -> " + displayName
                                + "   (also covers)");
                    }
                    return FilterResult.included("matched -D" + PROPERTY + "=" + id
                            + " through an 'Also covers' declaration");
                }
            }
        }

        return FilterResult.excluded("no -D" + PROPERTY + " id matches " + displayName);
    }

    /**
     * This case as {@code CaseIndex} addresses it - {@code DepositPageTest$InvalidDeposits#method}.
     *
     * @return null when the descriptor is not a method, which is nothing to worry about
     */
    private static String selectorOf(TestDescriptor descriptor) {
        return descriptor.getSource()
                .filter(MethodSource.class::isInstance)
                .map(MethodSource.class::cast)
                .map(source -> {
                    String className = source.getClassName();
                    return className.substring(className.lastIndexOf('.') + 1)
                            + "#" + source.getMethodName();
                })
                .orElse(null);
    }

    /**
     * Case id to the method that answers it, for every id the sources declare.
     *
     * <h2>Why it is a holder class</h2>
     * Building it parses every test source from disk. That is cheap - milliseconds - but it is
     * pointless on an ordinary run with no {@code -Dcase}, and it would happen during class
     * loading of the filter, which the platform does on every launch. A holder defers it to the
     * first id that fails to match a display name, so only a run that actually needs the lookup
     * pays for it.
     *
     * <h2>A failure here must not stop the run</h2>
     * The parse reads the test sources relative to the working directory, so a launch from
     * somewhere else finds nothing. That must degrade to "this id matched no display name",
     * which is the behaviour the filter had before, rather than to an exception thrown out of
     * discovery - which would fail the whole run over a lookup that most runs never use.
     */
    private static final class Aliases {

        static final Map<String, String> SELECTORS = load();

        private static Map<String, String> load() {
            try {
                Map<String, String> selectors = new HashMap<>();
                CaseIndex.byId().forEach((id, testCase) -> selectors.put(id, testCase.selector()));
                return selectors;
            } catch (Exception cannotRead) {
                System.out.println("[case] could not read the case index, so -D" + PROPERTY
                        + " matches display names only: " + cannotRead);
                return Map.of();
            }
        }
    }

    private static boolean matches(String displayName, String id) {
        String upper = displayName.toUpperCase(Locale.ROOT);
        if (!upper.startsWith(id)) {
            return false;
        }
        if (upper.length() == id.length()) {
            return true;
        }
        char next = upper.charAt(id.length());
        return !Character.isLetterOrDigit(next) && next != '-';
    }
}
