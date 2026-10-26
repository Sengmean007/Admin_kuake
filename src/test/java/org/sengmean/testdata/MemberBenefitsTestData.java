package org.sengmean.testdata;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

/**
 * The fixtures the Member Benefits workbook's cases call for.
 *
 * <h2>Every record the suite creates is prefixed {@value #PREFIX}</h2>
 * Mirrors {@code ShareLinkTestData}'s own rule: a suite that swept every Member Benefits record
 * would delete the client's real ones, so only records carrying this prefix are ever touched.
 *
 * <h2>Upload images - Si's own pictures</h2>
 * Re-sourced 2026-10-22 from {@code C:\Users\Acer\OneDrive\Pictures} (Si's "picture" folder) -
 * the ready-made banner PNGs there ({@code pc BANNER.png} and friends) are purpose-made for
 * other screens' own slot shapes, not this one, so every file here is still centre-cropped to
 * the shape its own slot describes rather than used as-is: icon 1:1 (256x256, from
 * {@code iCon/}), small banner 4:3 (480x360), mobile pop-up vertical 2:3 (600x900 -
 * {@code dog.jpg} was already portrait, the one source that needed no cropping), PC pop-up
 * horizontal 16:9 (960x540, {@code pc BANNER.png} itself for the primary one). Each slot has a
 * second, different photo for its replace-image rows. The boundary, oversized and
 * invalid-format files ({@code ImageFixtures}) are still generated, because a real photo cannot
 * hit an exact byte count or an exact pixel size.
 */
public final class MemberBenefitsTestData {

    private MemberBenefitsTestData() {
    }

    /** Marks a record as the suite's own. Never used by the client's data. */
    public static final String PREFIX = "QA MB ";

    public static final List<String> LANGUAGE_TABS =
            org.sengmean.page.webcontent.MemberBenefitsFormPage.LANGUAGE_TABS;

    // -----------------------------------------------------------------
    // Seed data - kept between runs, see MemberBenefitsSeedData
    // -----------------------------------------------------------------

    /** Marks a seed record, not {@link #PREFIX} - so {@code MemberBenefitsTest} never touches it. */
    public static final String SEED_PREFIX = "QA SEED MB ";

    public static final int SEED_COUNT = 10;

    /** Display orders 301-310 - clear of the 0/1/9999/duplicate values the suite's own order rows use. */
    public static final int SEED_ORDER_BASE = 300;

    public static String seedName(int n) {
        return SEED_PREFIX + String.format("%02d", n);
    }

    // -----------------------------------------------------------------
    // Benefit icon - 1:1, 256x256
    // -----------------------------------------------------------------

    public static final Path ICON = image("icon/icon.jpg");
    public static final Path ICON_REPLACEMENT = image("icon/icon-replacement.jpg");

    // -----------------------------------------------------------------
    // Banner slots - Small banner (4:3), Mobile pop-up (vertical 2:3), PC pop-up (16:9)
    // -----------------------------------------------------------------

    public static final Path SMALL_BANNER = image("banner/small-banner.jpg");
    public static final Path SMALL_BANNER_REPLACEMENT = image("banner/small-banner-replacement.jpg");

    public static final Path MOBILE_POPUP = image("banner/mobile-popup.jpg");
    public static final Path MOBILE_POPUP_REPLACEMENT = image("banner/mobile-popup-replacement.jpg");

    public static final Path PC_POPUP = image("banner/pc-popup.jpg");
    public static final Path PC_POPUP_REPLACEMENT = image("banner/pc-popup-replacement.jpg");

    // -----------------------------------------------------------------
    // Invalid uploads
    // -----------------------------------------------------------------

    private static final Path MALICIOUS_DIR = Path.of("target", "test-fixtures", "member-benefits");

    /** A minimal BMP - the one invalid format {@code ImageFixtures} does not generate. */
    public static Path bmp() {
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_RGB);
            Path out = MALICIOUS_DIR.resolve("invalid.bmp");
            java.nio.file.Files.createDirectories(MALICIOUS_DIR);
            javax.imageio.ImageIO.write(img, "bmp", out.toFile());
            return out;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** A plain text file renamed with a {@code .jpg} extension - MB-ADM-ADD-N023's "fake.jpg (text)". */
    public static Path textRenamedAsJpg() {
        try {
            java.nio.file.Files.createDirectories(MALICIOUS_DIR);
            Path out = MALICIOUS_DIR.resolve("fake.jpg");
            java.nio.file.Files.writeString(out, "not actually an image");
            return out;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** A file under {@code testdata/images/member-benefits/} on the test classpath, as a path Playwright can upload. */
    private static Path image(String relative) {
        String resource = "testdata/images/member-benefits/" + relative;
        URL url = MemberBenefitsTestData.class.getClassLoader().getResource(resource);
        if (url == null) {
            throw new IllegalStateException("Missing upload image " + resource + " under src/test/resources");
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot resolve " + url, e);
        }
    }
}
