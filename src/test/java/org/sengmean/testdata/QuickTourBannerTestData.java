package org.sengmean.testdata;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

/**
 * The fixtures {@code QuickTourBannerTest}'s workbook rows call for.
 *
 * <h2>Shapes, and which are confirmed</h2>
 * The sheet states the Add/Update Content Tab's own image hint text directly - Desktop
 * <b>2788 x 796 px</b>, Phone <b>750 x 264 px</b> ({@code QT-A-FORM_P04}). It does not state a
 * pixel size for Configure Page Banner's own PC horizontal / Mobile slots, so
 * {@link #PAGE_BANNER_PC} and {@link #PAGE_BANNER_MOBILE} are <b>assumed</b> to share the content
 * tab's own Desktop/Phone shapes - a page banner's "bottom banner" wording in {@code QT-A-PB_P06}
 * reads like the same horizontal strip, not a portrait mobile screen - rather than measured.
 * Confirm both assumed shapes against the live Configure Page Banner screen before trusting a
 * row that asserts on them; see {@code QuickTourPageBannerFormPage}'s own javadoc.
 *
 * <h2>Upload images - Si's own pictures</h2>
 * Sourced 2026-10-23 from {@code C:\Users\Acer\OneDrive\Pictures} (Si's "picture" folder), the
 * same source {@code MemberBenefitsTestData} and {@code OtherCategoryTestData} draw from. Every
 * file here is centre-cropped (letterboxed to cover, then cropped centred - scaled up first when
 * the source is smaller than the target in either dimension) to the exact pixel size its own slot
 * calls for, never used as-is:
 * <ul>
 *   <li>{@link #DESKTOP} / {@link #DESKTOP_REPLACEMENT} - 2788x796, from a UHD wallpaper
 *       ({@code 20260926_OHR.BearsEars...jpg}, 3040x880, the closer aspect match) and from
 *       {@code pc BANNER.png} (3000x800 - the same file {@code MemberBenefitsTestData} crops for
 *       its own, differently-shaped PC pop-up) respectively.</li>
 *   <li>{@link #PHONE} - {@code quick-tour banner.png}, already exactly 750x264, used as-is (no
 *       crop needed). {@link #PHONE_REPLACEMENT} is cropped from {@code photo_2026-09-26_09-50-39.jpg}
 *       (1280x419) to the same size, so a replace-image row actually swaps in different content
 *       rather than re-uploading a visually identical file.</li>
 *   <li>{@link #PAGE_BANNER_PC} / {@link #PAGE_BANNER_PC_REPLACEMENT} - cropped (and, for the
 *       first, scaled up slightly) from {@code photo_2026-08-25_10-11-31.jpg} (2070x690) and
 *       {@code photo_2026-09-26_09-50-39 (2).jpg} (1280x634).</li>
 *   <li>{@link #PAGE_BANNER_MOBILE} - {@code quick-tour banner mobile.png}, already exactly
 *       750x264, used as-is. {@link #PAGE_BANNER_MOBILE_REPLACEMENT} is cropped from a portrait
 *       Camera Roll photo already exactly 750 wide ({@code photo_2026-09-11_10-12-05.jpg},
 *       750x1008), so only its height needed cropping - the one slot that needed no horizontal
 *       scaling at all.</li>
 * </ul>
 * The boundary, oversized and invalid-format files ({@code ImageFixtures}) are still generated,
 * because a real photo cannot hit an exact byte count or an exact pixel size - see
 * {@link #textRenamedAsJpg()}, the {@code QT-A-PB_N07} non-image upload fixture.
 */
public final class QuickTourBannerTestData {

    private QuickTourBannerTestData() {
    }

    /** Marks a record this project created. Never used by the client's own data. */
    public static final String PREFIX = "QA QT ";

    // -----------------------------------------------------------------
    // Add/Update Content Tab - Desktop 2788x796, Phone 750x264 (QT-A-FORM_P04's own hint text)
    // -----------------------------------------------------------------

    public static final Path DESKTOP = image("content-tab/desktop/desktop.jpg");
    public static final Path DESKTOP_REPLACEMENT = image("content-tab/desktop/desktop-replacement.jpg");

    public static final Path PHONE = image("content-tab/phone/phone.jpg");
    public static final Path PHONE_REPLACEMENT = image("content-tab/phone/phone-replacement.jpg");

    // -----------------------------------------------------------------
    // Configure Page Banner - PC horizontal / Mobile, shape assumed (see class javadoc)
    // -----------------------------------------------------------------

    public static final Path PAGE_BANNER_PC = image("page-banner/pc/pc.jpg");
    public static final Path PAGE_BANNER_PC_REPLACEMENT = image("page-banner/pc/pc-replacement.jpg");

    public static final Path PAGE_BANNER_MOBILE = image("page-banner/mobile/mobile.jpg");
    public static final Path PAGE_BANNER_MOBILE_REPLACEMENT = image("page-banner/mobile/mobile-replacement.jpg");

    // -----------------------------------------------------------------
    // Invalid uploads - QT-A-PB_N07
    // -----------------------------------------------------------------

    private static final java.nio.file.Path MALICIOUS_DIR = Path.of("target", "test-fixtures", "quick-tour");

    /** A valid JPEG at exactly the given size - QT-A-FORM_P04's 50x50 and 3000x6000 off-size rows. */
    public static Path offSize(String name, int width, int height) {
        Path target = MALICIOUS_DIR.resolve(name + ".jpg");
        try {
            java.nio.file.Files.createDirectories(MALICIOUS_DIR);
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = image.createGraphics();
            g.setColor(java.awt.Color.LIGHT_GRAY);
            g.fillRect(0, 0, width, height);
            g.dispose();
            if (!javax.imageio.ImageIO.write(image, "jpg", target.toFile())) {
                throw new IllegalStateException("No ImageIO writer for jpg");
            }
            return target;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** A minimal one-page PDF - {@code QT-A-PB_N07} uploads this as a PC banner, which must be refused. */
    public static Path pdf() {
        try {
            java.nio.file.Files.createDirectories(MALICIOUS_DIR);
            Path out = MALICIOUS_DIR.resolve("invalid.pdf");
            String body = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                    + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                    + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\n"
                    + "trailer<</Root 1 0 R>>\n%%EOF\n";
            java.nio.file.Files.writeString(out, body);
            return out;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** A plain text file renamed with a {@code .jpg} extension, for a slot that should refuse it on content, not extension. */
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

    /** A file under {@code testdata/images/quick-tour/} on the test classpath, as a path Playwright can upload. */
    private static Path image(String relative) {
        String resource = "testdata/images/quick-tour/" + relative;
        URL url = QuickTourBannerTestData.class.getClassLoader().getResource(resource);
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
