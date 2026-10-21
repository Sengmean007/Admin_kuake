package org.sengmean;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.sengmean.base.BasePage;

/**
 * Scratch entry point for poking at the environment by hand.
 * The real coverage lives in src/test/java; run it with `mvn test`.
 */
public class Main {

    // Must be public static void main(String[] args): the previous `static void main()`
    // is an instance-main preview feature and is not a valid entry point on Java 17.
    public static void main(String[] args) {

        // try-with-resources so the browser and Playwright close even if navigation throws;
        // previously an exception left the Chromium process running.
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch();
            Page page = browser.newPage();
            // Whatever base.url resolves to, rather than an address spelled out a second time:
            // a scratch entry point that quietly goes somewhere other than the suites do is
            // worse than no scratch entry point.
            page.navigate(BasePage.BASE_URL);

            System.out.println("Title: " + page.title());
            browser.close();
        }
    }
}
