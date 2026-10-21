package org.sengmean.support;

import com.microsoft.playwright.Page;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;

/**
 * The image files the upload rows need, generated on first use rather than committed.
 *
 * <h2>Why generate them</h2>
 * The workbook asks for a valid PNG, JPG, SVG and WEBP, a GIF and a PDF that must be refused, a
 * 9:16 banner, a 4:3 one and an oversized one. Committing a dozen binaries for that would put
 * images nobody reviews into the history, and each would have to be re-made by hand the day a
 * limit changes. These are made in milliseconds under {@code target/}, so {@code mvn clean} removes
 * them and the next run makes them again.
 *
 * <p>The valid icons and banners are the exception: since 2026-09-25 they are Si's own pictures,
 * kept under {@code src/test/resources/testdata/images/other-category} and named in
 * {@code OtherCategoryTestData}. What is generated here is only what those pictures do not cover.
 *
 * <h2>WEBP comes from the browser</h2>
 * {@code javax.imageio} cannot write WEBP. Chromium can - {@code canvas.toDataURL("image/webp")} -
 * and the suite already has a Chromium open, so {@link #webp} borrows the page it is given. The
 * file is then as real a WEBP as any the client would upload.
 *
 * <h2>The oversized file is a real JPEG, padded</h2>
 * A small valid JPEG with zero bytes appended after its end-of-image marker, up to the requested
 * size. Every decoder stops at the marker, so the file opens as the image it is; what is being
 * tested is the size limit, and the file hits it exactly rather than approximately - which a
 * noise-filled image of "roughly" the right size would not.
 */
public final class ImageFixtures {

    private static final Path DIR = Path.of("target", "test-fixtures", "other-category");

    private ImageFixtures() {
    }

    /** A PNG of the given size and colour. */
    public static Path png(String name, int width, int height, Color colour) {
        return raster(name + ".png", "png", width, height, colour);
    }

    /** A JPG of the given size and colour. */
    public static Path jpg(String name, int width, int height, Color colour) {
        return raster(name + ".jpg", "jpg", width, height, colour);
    }

    /** A GIF - a valid image in a format neither upload accepts. */
    public static Path gif(String name, int width, int height, Color colour) {
        return raster(name + ".gif", "gif", width, height, colour);
    }

    /** A small, valid SVG. */
    public static Path svg(String name, Color colour) {
        String hex = String.format("#%02x%02x%02x", colour.getRed(), colour.getGreen(), colour.getBlue());
        return write(name + ".svg", ("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"128\" height=\"128\" "
                + "viewBox=\"0 0 128 128\"><circle cx=\"64\" cy=\"64\" r=\"56\" fill=\"" + hex + "\"/></svg>")
                .getBytes(StandardCharsets.UTF_8));
    }

    /** A minimal one-page PDF - a document, not an image, for the rows that must refuse it. */
    public static Path pdf(String name) {
        String body = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\n"
                + "trailer<</Root 1 0 R>>\n%%EOF\n";
        return write(name + ".pdf", body.getBytes(StandardCharsets.US_ASCII));
    }

    /** A WEBP, encoded by the Chromium behind {@code page}. */
    public static Path webp(Page page, String name, int width, int height, Color colour) {
        Path target = DIR.resolve(name + ".webp");
        if (Files.exists(target)) {
            return target;
        }
        String css = String.format("rgb(%d,%d,%d)", colour.getRed(), colour.getGreen(), colour.getBlue());
        Object dataUrl = page.evaluate("([w, h, c]) => { const k = document.createElement('canvas');"
                + " k.width = w; k.height = h; const g = k.getContext('2d'); g.fillStyle = c; g.fillRect(0, 0, w, h);"
                + " g.fillStyle = '#fff'; g.fillRect(w / 8, h / 8, w / 4, h / 4);"
                + " return k.toDataURL('image/webp', 0.9); }", Arrays.asList(width, height, css));
        String text = String.valueOf(dataUrl);
        if (!text.startsWith("data:image/webp")) {
            throw new IllegalStateException("This Chromium would not encode WEBP (got "
                    + text.substring(0, Math.min(30, text.length())) + "), so no WEBP fixture can be made.");
        }
        return write(name + ".webp", Base64.getDecoder().decode(text.substring(text.indexOf(',') + 1)));
    }

    /** A valid 4:3 JPEG padded after its end marker to exactly {@code bytes}. */
    public static Path oversizedJpg(String name, int bytes) {
        Path target = DIR.resolve(name + ".jpg");
        try {
            if (Files.exists(target) && Files.size(target) == bytes) {
                return target;
            }
            byte[] image = Files.readAllBytes(jpg(name + "-core", 800, 600, Color.ORANGE));
            byte[] padded = Arrays.copyOf(image, Math.max(bytes, image.length));
            return write(name + ".jpg", padded);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path raster(String fileName, String format, int width, int height, Color colour) {
        Path target = DIR.resolve(fileName);
        if (Files.exists(target)) {
            return target;
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(colour);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.fillRect(width / 8, height / 8, width / 4, height / 4);
        g.dispose();
        try {
            Files.createDirectories(DIR);
            if (!ImageIO.write(image, format, target.toFile())) {
                throw new IllegalStateException("No ImageIO writer for " + format);
            }
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path write(String fileName, byte[] bytes) {
        try {
            Files.createDirectories(DIR);
            return Files.write(DIR.resolve(fileName), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
