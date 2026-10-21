package org.sengmean.testdata;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

/**
 * The fixtures the Share Link workbook's cases call for.
 *
 * <h2>Every record the suite creates is prefixed {@value #PREFIX}</h2>
 * Mirrors {@code OtherCategoryTestData}'s own rule: a suite that swept every Share Link record
 * would delete the client's real cards, so only records carrying this prefix are ever touched.
 *
 * <h2>Upload images - Si's own pictures</h2>
 * Prepared 2026-10-21 from Si's local Pictures folder. The spec's recommended card image is
 * 704x396 (16:9) - neither original is that shape (they are phone-portrait photos), so the two
 * used as "valid" card images are centre-cropped to 16:9 and scaled to 704x396; the third is kept
 * at its original, off-ratio portrait size on purpose, for the row that checks a non-16:9 upload
 * ({@code SL-ADM-VAL-015}) - the design accepts it and crops it on the client rather than refusing
 * it. The boundary, oversized and invalid-format files ({@code ImageFixtures}) are still generated,
 * because a real photo cannot hit an exact byte count or an exact 352x198.
 */
public final class ShareLinkTestData {

    private ShareLinkTestData() {
    }

    /** Marks a record as the suite's own. Never used by the client's data. */
    public static final String PREFIX = "QA SL ";

    public static final List<String> LANGUAGE_TABS = org.sengmean.page.webcontent.ShareLinkFormPage.LANGUAGE_TABS;

    // -----------------------------------------------------------------
    // Upload images - Si's own pictures
    // -----------------------------------------------------------------

    /** A real photo, centre-cropped to 16:9 and scaled to 704x396 - the main "valid card image" fixture. */
    public static final Path CARD_IMAGE = image("card/wetv-pro-16x9.jpg");

    /** A second, different photo for the replace-image rows - 16:9, 704x396. */
    public static final Path CARD_IMAGE_REPLACEMENT = image("card/replacement-16x9.jpg");

    /** The third photo, untouched - portrait, off-ratio (706x1280) - {@code SL-ADM-VAL-015}. */
    public static final Path CARD_IMAGE_OFF_RATIO = image("card/portrait-off-ratio.jpg");

    // -----------------------------------------------------------------
    // Malicious uploads - SL-NFR-SEC-005
    // -----------------------------------------------------------------

    private static final java.nio.file.Path MALICIOUS_DIR =
            java.nio.file.Path.of("target", "test-fixtures", "share-link");

    /** A JPG with PHP source appended after its data - a polyglot upload a naive extension check would accept. */
    public static Path jpgWithPhpPayload() {
        return writeMalicious("polyglot.jpg",
                readBytes(CARD_IMAGE), "\n<?php system($_GET['c']); ?>\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** A .png that is actually HTML content - tests whether the check reads bytes, not just the extension. */
    public static Path pngWithHtmlContent() {
        return writeMalicious("spoofed.png",
                "<html><body><script>alert(document.cookie)</script></body></html>"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** An SVG carrying an inline {@code <script>} - SVG is XML, so this is a valid file or a stored-XSS vector. */
    public static Path svgWithScript() {
        return writeMalicious("script.svg",
                ("<svg xmlns='http://www.w3.org/2000/svg' width='704' height='396'>"
                        + "<script>alert(document.cookie)</script></svg>").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** A Windows executable's header, renamed with a {@code .jpg} extension - "virus.exe -> a.jpg" in the sheet. */
    public static Path exeRenamedAsJpg() {
        return writeMalicious("virus-renamed.jpg", new byte[] {'M', 'Z', 0, 0, 0, 0, 0, 0});
    }

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

    private static byte[] readBytes(Path file) {
        try {
            return java.nio.file.Files.readAllBytes(file);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static Path writeMalicious(String name, byte[]... parts) {
        try {
            java.nio.file.Files.createDirectories(MALICIOUS_DIR);
            Path out = MALICIOUS_DIR.resolve(name);
            try (var stream = java.nio.file.Files.newOutputStream(out)) {
                for (byte[] part : parts) {
                    stream.write(part);
                }
            }
            return out;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** A file under {@code testdata/images/share-link/} on the test classpath, as a path Playwright can upload. */
    private static Path image(String relative) {
        String resource = "testdata/images/share-link/" + relative;
        URL url = ShareLinkTestData.class.getClassLoader().getResource(resource);
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
