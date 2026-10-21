# Admin_kuake

> Cloned from `Admin_weibo` on 2026-10-21 - same framework, pointed at a different back office
> (`admin-test.138hk.vip`). The rest of this file still describes the Weibo Empire project it was
> written against.

UI test automation for the **Weibo Empire admin back office**, driven by Playwright and JUnit 5.

The framework is the one the sibling [`NewWeibo_automation`](../NewWeibo_automation) project uses
against the member site — the same base classes, the same `Config/` settings, the same self-updating
reports, the same `-Dcase=` launcher plumbing — carried over so the two projects behave identically
and a tester who knows one knows the other.

> **This is a scaffold. There are no page objects and no test cases yet.**
> `./mvnw test` compiles and runs clean, and reports `Tests run: 0`. What is here is everything a
> case needs in order to exist: see [Where to start](#where-to-start).

---

## Requirements

- **JDK 17.** The POM sets `maven.compiler.release=17`, so a newer JDK compiles fine, but 17 is
  what CI builds on and what the Maven wrapper is exercised with.
- **No Maven install needed.** The wrapper is committed and pins Maven 3.9.9, which it downloads
  on first use. Use `./mvnw`, never a bare `mvn` — see [Conventions](#conventions).
- **No browser install needed.** Playwright downloads and versions its own Chromium the first
  time a suite runs. There is no driver to keep in step with an installed browser.

## Quick start

```bash
cd Admin_kuake
./mvnw test
```

The first run is slow — it fetches Maven and a browser.

To watch a run instead of running headless:

```bash
./mvnw test -Dheadless=false -DslowMo=500
```

---

## Where the suites point

`Config/config.properties` holds the ordinary answer for every run:

| Setting | What it holds |
|---|---|
| `base.url` | The admin back office under test, `https://admin-test.1083.city/`. |
| `test.username` / `test.password` | The back-office operator account the suites sign in as (`qa0001`, Super Admin). |
| `client.url`, `test.user.*` | The member site and the member the Notification inbox rows read (`qa000092`). |

Any of them can be set for one run rather than edited — `./mvnw test -Dbase.url=... -Dtest.username=...` —
but the committed file should end up holding the ordinary answer.

## Where to start

1. **Point `base.url` at the back office** and put an operator account in `Config/`.
2. **Write `page.auth.AdminLoginPage`**, extending `org.sengmean.base.BasePage`.
3. **Add the shared `signIn` helper** to `BaseTest`. There is a javadoc note at exactly the spot
   it belongs, explaining what the member-site version did and which half of it carries over.
4. **Write the first test class** under `tests/auth/`, extending `BaseTest`, with an
   `@DisplayName` that begins with the case id.
5. **Add a `@Suite`** under `suites/`, and a row for it in `TestSuiteController.suites()`.

Each of those packages has a `package-info.java` stating the conventions that apply inside it.

---

## Running tests

```bash
./mvnw test                                  # everything
./mvnw test -Dsuite=MemberSuite              # one area
./mvnw test -Dtest=AdminLoginTest            # one class
./mvnw test -Dcase=ADMLOGIN-001              # one case, by its test-case id
./mvnw test -Dcase=ADMLOGIN-001,ADMLOGIN-002 # several
```

**Use `-Dsuite`, not `-Dtest`, for a suite class.** Surefire turns `-Dtest` into a *method* filter
applied to everything it discovered, so `-Dtest=MemberSuite` resolves the suite, then filters out
every one of its cases for not being called `MemberSuite` — and reports **success** with
`Tests run: 0`. `-Dsuite` feeds the include pattern instead, which is applied at scanning.

**Use `-Dcase` to run one case by id.** The case id lives in the `@DisplayName`, which `-Dtest`
cannot match, so `-Dtest=ADMLOGIN-001` would also select nothing and say so as `Tests run: 0`.

### Without a terminal

- **`TestCase/run/*.cmd`** — one double-clickable launcher per case, generated at the end of every
  run from the ids the sources carry, so a case added or renamed today cannot leave yesterday's
  `.cmd` behind. Empty until there are cases.
- **`awb://` links** — a case id in a spreadsheet becomes a Run button. Register the handler once
  with `tools/install-url-handler.ps1`, then `=HYPERLINK("awb://ADMLOGIN-001", "Run")` works from
  the sheet. Re-register it if the repository moves; the registry entry holds an absolute path.

> **The scheme is `awb://` here, not `NewWeibo_automation`'s `fpr://`,** and
> `tools/run-server.ps1` listens on **8766** rather than 8765. Both projects can be installed on
> one machine without one stealing the other's Run links. For the same reason the Swagger UI app
> is on **8081** here and 8080 there.

---

## Safety: two tags that decide what an unattended run may do

| Tag | Meaning |
|---|---|
| `creates-data` | Side effects that outlive the run — approving or rejecting a transaction, creating or editing a member, changing a setting another suite depends on. |
| `known-defect` | Asserts the behaviour the back office *should* have, so it fails today by design. It is the regression net for open findings, not a health check. |

The safe set, and what every scheduled run uses:

```bash
./mvnw test -DexcludedGroups=known-defect,creates-data
```

Excluded cases still appear in the report as **Not run**, so the gap is visible rather than
silent. Run the spending ones deliberately, attended, where you can watch what they cost.

**A back office makes this tagging matter more than it did on the member site.** A member-site case
spends one account's balance; a back-office case can approve a withdrawal, change a member's tier
or edit a promotion, and the blast radius is every other suite that reads that data. When in doubt
about a case, tag it `creates-data`.

---

## Configuration

Settings live in **`Config/config.properties`** — the environment, the accounts, the captcha, the
browser window, the settle timeouts and every output path, each with a note explaining it. It is
committed and holds no secrets.

`Config/secrets.properties` is the uncommitted sibling for anything that must not be in git;
`Config/secrets.properties.example` is the template.

**Precedence, highest first:**

1. `-Dkey=value` on the command line
2. the environment variable — same name, upper case, dots as underscores (`base.url` → `BASE_URL`)
3. `Config/secrets.properties`
4. `Config/config.properties`

That is what lets CI pass credentials as environment variables without putting them in a command
line that gets logged. **Do not move a setting back into the POM**: Surefire plants
`systemPropertyVariables` into the forked JVM *after* Maven parses the command line, so a literal
there silently beats any `-D` you pass.

---

## Currency and seed data

### Choosing the currency a run works in

The back office header has a currency selector, and it is **not** a display filter: the back office
keeps separate records per currency (Rebate set up holds a different Min. Claim for USDT and HKD, for
example). So every signed-in suite first switches the header to one currency, and reads and writes
only there.

| Where | How |
|---|---|
| Default for every run | `test.currency=HKD` in `Config/config.properties` |
| One run only | `-Dtest.currency=<code>` on the command line |
| Offered by the site | `Fiat Currency`, `USDT`, `TRX`, `THB`, `HKD`, `USD` |

```bash
./mvnw test -Dsuite=NotificationListSuite                        # runs in HKD (the default)
./mvnw test -Dsuite=NotificationListSuite -Dtest.currency=USDT   # runs in USDT
```

The switch is `AdminShellPage.useCurrency(code)`, called right after sign-in. A misspelt code fails
the run immediately and lists the currencies the site does offer, so a run never quietly carries on
in the header's default. The Rebate, Other Category and Notification suites and both seed classes
all switch to `test.currency`.

> **Use the same `-Dtest.currency` for a seed, the suite that relies on it, and its cleanup.**
> A seed made in HKD is not visible to a run in USDT.

### Seed data for the paging cases

The pagination cases need more records than the list's smallest page (20) holds, and the test site's
lists are normally near empty. Two seed suites create that data once and **keep it** between runs;
two cleanup suites remove it.

| Suite | Case | What it does |
|---|---|---|
| `OtherCategorySeedSuite` | `OC-CRT-902` | Creates Other Categories `QA SEED 01`..`QA SEED 20` (Active, Display Orders 201–220, no games) |
| `OtherCategorySeedCleanupSuite` | `OC-DEL-901` | Deletes every `QA SEED` category |
| `NotificationSeedSuite` | `NOT-CRT-901` | Creates notifications `QA SEED NOT 01`..`QA SEED NOT 50` |
| `NotificationSeedCleanupSuite` | `NOT-DEL-901` | Deletes every `QA SEED NOT` notification |

```bash
# Notification paging (NOT-LST-005 / NOT-LST-006)
./mvnw test -Dsuite=NotificationSeedSuite            # once: 50 notifications, 3 pages at 20 per page
./mvnw test -Dsuite=NotificationListSuite            # the paging cases now have pages to move through
./mvnw test -Dsuite=NotificationSeedCleanupSuite     # when finished

# Other Category paging (OC-LST-005 / OC-LST-006)
./mvnw test -Dsuite=OtherCategorySeedSuite
./mvnw test -Dsuite=OtherCategoryListSuite
./mvnw test -Dsuite=OtherCategorySeedCleanupSuite

# Seeding another currency - pass the same currency to the suite and the cleanup too
./mvnw test -Dsuite=NotificationSeedSuite -Dtest.currency=USDT
```

How the seed behaves:

- **Safe to re-run.** A seed suite counts what is already listed and creates only the missing records.
- **It survives ordinary runs.** The suites sweep only their own prefixes (`QA OC `, `QA NOT `), and
  the seeds use `QA SEED` / `QA SEED NOT`. The seed classes do not end in `Test`, so a plain
  `./mvnw test` or `ToolSuite` / `ProductSuite` never seeds or deletes; only the four suites above do.
- **Seeded notifications are never sent.** Every one is saved **Inactive**, on a Daily 03 AM schedule,
  and targeted only at the configured groups (`test.notification.agentGroup` /
  `test.notification.memberGroup`), never Select all.
- **The ids are project-only** (the 900 block), because the client workbook has no seed rows.
- **It takes a while.** Each record goes through the real form; 50 notifications is very roughly
  10–15 minutes. Keep the machine awake — a sleeping PC pauses the run, and it usually fails on
  waking.

---

## Reports

Every run rewrites them. There is no second command to remember — a report you have to ask for is
a report that goes stale.

| Path | What it answers |
|---|---|
| `docs/reports/` | How did this run go, case by case |
| `docs/reports/extent/` | The same run as a dashboard, with charts, filters and search |
| `docs/test-cases/` | Which of the client's rows we automate, each with a Run link |
| `docs/reports/cases/` | The client's own workbook with this run's results written into it |

Each row opens a detail panel holding the case as its source declares it, the full result, and the
run's own record of the browser — page loads, the site's API calls with the status each came back
with, console errors, screenshots. A passing row carries that trail too: a green result is a claim,
and the trail is the working behind it.

See [`docs/README.md`](docs/README.md) for the full description.

---

## The client test case workbook

`src/test/resources/testdata/client_testcase.xlsx` is a **copy** of the client's admin test case
sheet (the Rebate, Other Category and Notification tabs), checked in so the project can be read and
run without an account on someone else's Drive. The runs never write back to it. After re-exporting
the client's sheet into it:

```bash
./mvnw exec:java@append-cases   # re-adds the project's own rows (testdata/added-cases/*.tsv)
./mvnw exec:java@split-cases    # builds the per-feature workbooks under testdata/features
./mvnw exec:java@case-page      # rebuilds docs/test-cases
```

Cases the project adds to a client tab - numbered on from the tab's last id - are kept in
`src/test/resources/testdata/added-cases/<feature>.tsv`, because anything typed straight into the
copy is lost on the next export. `append-cases` writes them after the last row of their section
and skips ids already there, so it is safe to run again; pass another workbook as
`-Dexec.args="C:/path/copy.xlsx"` to bring a copy of the same tab up to date.

A row's id and the suite's id are the same case under two spellings — `TC_LOGIN_001` and
`LOGIN-001` — and `CaseSheetLayout` converts between them. A case whose `@DisplayName` carries an
id is linked to its row; a case that answers several rows names the rest in an
`// Also covers workbook rows:` comment, which keeps the report's Id column a key rather than a
list while still producing a Run button on every row named.

> Several tabs in the member workbook generate their ids from `ROW()`, so deleting a row upstream
> renumbers every id below it. Check one known row after any re-import before trusting the
> mappings.

`exec:java@trim-cases` exists too, but it is tuned to the member workbook's out-of-scope `TC_TP_`
rows. Point it at whatever the admin sheet's equivalent turns out to be, or leave it unused.

---

## Project structure

```
Config/                      settings, and the template for the uncommitted secrets file
TestCase/run/                one generated .cmd launcher per case
docs/                        generated reports and client test case pages
tools/                       PowerShell helpers: the awb:// handler, the case runner
src/main/java/org/sengmean/
  base/                      BasePage
  config/                    ConfigDataProvider
  page/<area>/               page objects, grouped by area of the back office
  report/                    the Excel and HTML report generators
  testcase/                  the launcher generator and the workbook trimmer
  api/                       a standalone Swagger UI app, independent of the suites
src/test/java/org/sengmean/
  base/                      BaseTest, ScreenshotOnFinish
  suites/                    one @Suite per area
  support/                   CaseIdFilter, which is what -Dcase reaches
  tests/<area>/              the tests, mirroring page/<area>
```

Page objects and their tests are grouped **by area** into mirrored sub-packages, so a suite and the
pages it drives share a package name: `page.payment` ↔ `tests.payment`.

**A page object must not return a `Locator` to a test.** Element work belongs in the page layer; a
test that holds a `Locator` has a locator to maintain in two places.

---

## Continuous integration

- **`.github/workflows/build.yml`** — compiles on pushes and pull requests to `main`, `master` and
  `dev`. It runs no tests: the suites drive a live third-party site and fail for reasons unrelated
  to any change, and gating a pull request on that trains everyone to ignore a red tick.
- **`.github/workflows/qa-suite.yml`** — the safe set against the test environment, on demand.
- **`Jenkinsfile`** — a parameterised pipeline with a nightly safe-set schedule. Create a Pipeline
  job with *Pipeline script from SCM* pointing at this repository; nothing else is configured in
  the UI.

Both CI definitions run the safe set only, and neither offers a way to widen it. These are
integration tests against a live third-party site: they fail for reasons unrelated to any commit,
so a red run is a signal to look rather than proof of a regression.

---

## The Swagger UI app

A standalone Spring Boot app documenting this project's own endpoints. It has no effect on
`./mvnw test` — the suites never start a Spring context.

```bash
./mvnw spring-boot:run     # then http://localhost:8081/swagger-ui.html
```

It is behind HTTP Basic. Override the credentials with **environment variables**
(`API_DOCS_USER`, `API_DOCS_PASSWORD`) rather than `-D`: `spring-boot:run` forks a JVM that does
not inherit Maven's system properties.

---

## Conventions

- **Always `./mvnw`, never a bare `mvn`.** The wrapper is what CI runs and what pins the Maven
  version; a plain `mvn` is not on the PATH of every machine this is developed on.
- **Every test-case id prefix comes from the client workbook.** Cases that exist only in this
  project — no row in the sheet — take the **900 block** of the same prefix, so it is always
  obvious which rows a client reading the report can look up and which are ours.
- **One case carries exactly one id, and no id is used twice.** Extra rows a case answers for go in
  an `// Also covers workbook rows:` comment.
- **Do not renumber ids to close a gap.** A missing number usually means a workbook row that is
  deliberately not automated, and shifting the others would point every id at the wrong row.
- **Claim a row only if the test actually visits it.** A Run button on a row the test skips is
  worse than no button: the row reports green having been skipped.

[`AGENTS.md`](AGENTS.md) carries the working notes — what each suite costs, and the traps it cost
to find. It is nearly empty today; add to it as the suites are written.
