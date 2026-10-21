package org.sengmean.support;

import com.microsoft.playwright.Browser;

/**
 * The devices the client-side UI rows are run on - a Windows PC, a Mac, a tablet and two phones.
 *
 * <h2>Why a phone is more than a narrow window</h2>
 * The member site decides what to render from more than the width: with a mobile user agent and
 * touch it serves its own header, a bottom tab bar and a ☰ menu, and a desktop browser squeezed to
 * 390px gets none of that. So each profile carries the viewport, pixel density, touch and user
 * agent of the real device - ported from NewWeibo_automation's {@code support.Device}, which
 * measured them on 2026-09-23.
 *
 * <h2>Why an engine</h2>
 * Compatibility is about the browser as much as the screen. Safari on a Mac, an iPad or an
 * iPhone is WebKit, and Chromium pretending to be an iPhone exercises Chromium's layout engine,
 * not Safari's. Each device therefore names the Playwright engine it runs in; the Mac and iOS
 * profiles need {@code webkit} installed ({@code ./mvnw exec:java@playwright-install} or
 * {@code npx playwright install webkit}), and a row whose engine cannot launch says so.
 */
public enum Device {

    DESKTOP_PC("PC (Windows, Chrome)", Engine.CHROMIUM, 1920, 1080, 1.0, false,
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"),

    DESKTOP_MAC("Mac (macOS, Safari)", Engine.WEBKIT, 1440, 900, 2.0, false,
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15"),

    IPAD("iPad (iPadOS, Safari)", Engine.WEBKIT, 820, 1180, 2.0, true,
            "Mozilla/5.0 (iPad; CPU OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1"),

    IPHONE("iPhone 14 (iOS, Safari)", Engine.WEBKIT, 390, 844, 3.0, true,
            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1"),

    ANDROID("Android (Pixel 7, Chrome)", Engine.CHROMIUM, 412, 915, 2.625, true,
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

    /** The Playwright browser engine a device runs in. */
    public enum Engine { CHROMIUM, WEBKIT }

    public final String label;
    public final Engine engine;
    public final int width;
    public final int height;
    public final double scale;
    public final boolean touch;
    public final String userAgent;

    Device(String label, Engine engine, int width, int height, double scale, boolean touch, String userAgent) {
        this.label = label;
        this.engine = engine;
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.touch = touch;
        this.userAgent = userAgent;
    }

    /** True for the phones, which the site serves its mobile layout and its Tailwind {@code sm} breakpoint (640px) excludes. */
    public boolean isPhone() {
        return width < 640;
    }

    public Browser.NewContextOptions contextOptions() {
        return new Browser.NewContextOptions()
                .setViewportSize(width, height)
                .setDeviceScaleFactor(scale)
                .setIsMobile(touch)
                .setHasTouch(touch)
                .setUserAgent(userAgent);
    }

    @Override
    public String toString() {
        return label + " " + width + "x" + height;
    }
}
