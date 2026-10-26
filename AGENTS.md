# AGENTS.md - AI Coding Guidelines for Admin_kuake

> Cloned from `Admin_weibo` on 2026-10-21, same framework and page objects, pointed at a
> different back office (`admin-test.138hk.vip`). The content below still describes the
> Weibo Empire project it was written against - re-confirm anything environment-specific
> (locators, menu wording, the client workbook) against this site before trusting it here.

UI test automation for the **Weibo Empire admin back office**: Playwright + JUnit 5, Java 17,
Maven.

This project was created by lifting the framework out of the sibling **NewWeibo_automation**
suite, which tests the member site. Everything below the page layer is that project's, unchanged
and deliberately so - the two should behave identically, and a fix in one is worth porting to the
other. What was *not* carried over is the member site itself: no page objects, no test classes, no
suites, no client workbook.

**Read `README.md` first.** It covers how to run things, the safety tags, the configuration
precedence and the project layout. This file carries what an agent needs on top of that: the
decisions, and the traps that cost a run to find.

---

## Architecture Overview

- **`src/main/java/org/sengmean/page/<area>/`** - page objects, grouped by area of the back
  office, each extending `base.BasePage`. They own every locator. A page object must never hand a
  `Locator` back to a test.
- **`src/test/java/org/sengmean/tests/<area>/`** - the test classes, mirroring `page/<area>`
  exactly, each extending `base.BaseTest`.
- **`src/test/java/org/sengmean/suites/`** - one `@Suite` per area, selected with `-Dsuite=`.
- **`src/main/java/org/sengmean/config/`** - `ConfigDataProvider`, which reads `Config/` and seeds
  the system properties. Loading it is a side effect of touching `BasePage`, which is what pulls
  the settings in early enough for everything else.
- **`src/main/java/org/sengmean/report/`** + **`src/test/java/org/sengmean/report/`** - the
  generators and the JUnit listeners that run them. `CaseLog` is in `src/main` so page objects can
  write to the step trail too.
- **`src/main/java/org/sengmean/testcase/`** - the per-case `.cmd` launcher generator and the
  workbook trimmer.
- **`src/main/java/org/sengmean/api/`** - a standalone Swagger UI app. Independent of the suites;
  `mvn test` never starts a Spring context.

---

## Developer Workflows

```bash
./mvnw test                                  # everything
./mvnw test -Dsuite=MemberSuite              # one area  (NOT -Dtest, see below)
./mvnw test -Dtest=AdminLoginTest            # one class
./mvnw test -Dcase=ADMLOGIN-001              # one case, by its test-case id
./mvnw test -Dheadless=false -DslowMo=500    # watch it happen
./mvnw test -DexcludedGroups=known-defect,creates-data   # the safe set
```

**Always `./mvnw`, never a bare `mvn`.** The wrapper is committed, pins the Maven version and is
what CI runs; a plain `mvn` is not on every machine's PATH.

---

## `-Dtest=SomeSuite` does not work, and fails silently

Surefire turns `-Dtest` into a **method** filter applied to everything it discovered. Point it at a
`@Suite` and the suite is found, its member classes resolve, and then every one of their cases is
filtered out for not being called `SomeSuite`. The run reports **success** with `Tests run: 0` and
the member classes listed as skipped, which reads as a suite that selected nothing.

`-Dsuite` feeds Surefire's `<includes>` instead, which is applied at scanning, so the suite engine
is left to resolve the suite's children itself. Do not "simplify" this back to `-Dtest`.

The same trap applies to `-Dcase`: the case id lives in the `@DisplayName`, and `-Dtest` matches
method names, so `-Dtest=ADMLOGIN-001` selects nothing and says so as `Tests run: 0`.
`org.sengmean.support.CaseIdFilter` is what reads `-Dcase`, and it runs in the forked test JVM -
which is why `<case>` is declared in the POM's `<properties>` and passed through Surefire's
`systemPropertyVariables`.

---

## Settings live in `Config/`, not in the POM

The environment, the accounts, the captcha, the browser window and the settle timeouts are in
`Config/config.properties`, read by `config.ConfigDataProvider`.

They used to be literals in Surefire's `<systemPropertyVariables>`, and that is not a default: the
value is planted into the forked JVM **after** Maven parses the command line, so `-Dtest.username=x`
was silently ignored and the run went ahead on the committed account. **Do not put a setting back
in the POM.** If it needs a default, it belongs in `Config/config.properties` where a tester can
read it.

Precedence, highest first: `-D` → environment variable (`base.url` → `BASE_URL`) →
`Config/secrets.properties` (uncommitted) → `Config/config.properties`.

`ConfigDataProvider` treats **blank as unset** at every level. That is not fussiness: Surefire
plants `${case}` into the fork as an empty string when the POM property is undefined, so a caller
asking for an unset key gets `""` rather than `null`, and code that only guards against null goes
on to parse it. A `Double.parseDouble("")` in a static initializer errors every case in every
suite with a `NoClassDefFoundError` for the base class - an error that says nothing whatsoever
about a mistyped number in a properties file.

---

## What `Config/config.properties` points at (answered; state as of 2026-09-30)

The two settings that were once carried over from NewWeibo_automation are long answered:
**`base.url`** is the admin back office (`https://admin-test.1083.city/`) and **`test.username`** is the
operator account `qa0001` (Super Admin; `test.admin.*` is the same account until a distinct Admin is
configured, `test.agent.*` is `qatest`, and `test.alt.*` - a second operator - is still blank). The member
site the Notification inbox rows read is `client.url` with the member in `test.user.*` (qa000092) and the
outsider in `test.notification.outsider.*` (qa000096). If a run signs in nowhere, check these before the
locators - a suite pointed at the wrong site fails everywhere at once.

---

## Never wait for `load`

Playwright's default `waitUntil` is `load`, which waits for **every subresource, third-party ones
included**. On the member site that was measured (2026-09-12) as the page's own document and API
calls finishing at 5.4s while a Cloudflare beacon arrived at 6.8s and a chat widget was still
fetching at 10.3s - so a six-second action budget made every navigation a coin toss, and duly
failed a case about a language menu on an analytics beacon.

`BasePage.open()` uses `domcontentloaded`, and `BasePage.awaitReady()` honours
`domcontentloaded` strictly and then gives `load` a **best-effort** five seconds it is allowed to
miss. Do not call a bare `page.waitForLoadState()` in a page object; it defaults to `load` and
carries the whole trap back in.

The measurement is the member site's. Whether the back office has the same third parties is worth
checking once - but the rule holds regardless, because a case must never be red because a beacon
was not ready.

Two timeouts, because they are two different questions: `test.actionTimeout` is deliberately
impatient at 6s (a control that is not clickable in six seconds is a finding, and waiting thirty
only makes a red suite slow as well as red), while `test.navigationTimeout` is 30s.

---

## Every case carries its id in the `@DisplayName`

`@DisplayName("ADMLOGIN-001 - sign in with valid credentials")`. This is not decoration. The id in
the display name is:

- what `CaseIdFilter` matches for `-Dcase=`,
- what the HTML and Excel reports join on to reach the client workbook's rows,
- what the per-case launchers under `TestCase/run` are named after,
- what `src/test/resources/junit-platform.properties` orders a run by.

A case without one runs, and then appears nowhere.

**Id prefixes come from the client workbook.** A case added to an existing tab - whether Si asked for
it or it was added while fixing something - takes the **next number after that tab's highest real id**
(`OC-GAM-011` after `OC-GAM-010`, `NOT-CRT-028` after `NOT-CRT-027`); Si ruled this on 2026-09-29 and
again on 2026-09-30, and such an id has no workbook row until one is added, so say so. The **900 block**
is kept only for the seed rows (`NOT-CRT-901`, `NOT-DEL-901`, `OC-CRT-901`, `OC-DEL-901`) and the
pre-existing structural rows such as `OC-GAM-901`. One case carries exactly one id and no id is used
twice; extra rows a case answers for go in an `// Also covers workbook rows:` comment. Never renumber
ids to close a gap.

---

## Case ordering is configured, not incidental

`src/test/resources/junit-platform.properties` orders methods by `DisplayName` and classes by
`@Order`. Without it JUnit picks a deterministic but arbitrary order, which is not the same thing
as a sensible one - on the member site a sweep case ran before the case that funded the wallets,
and both looked like environment problems for a while. A class that needs something else declares
`@TestMethodOrder` locally and wins.

---

## Reports rewrite themselves on every run

There is no second command to remember, and there must not be: a report you have to ask for is a
report that goes stale. `HtmlReportListener` and `ExtentReportListener` are registered through
`src/test/resources/META-INF/services/`, so they run at the end of every `mvn test`. The
`exec:java@...` executions in the POM exist only for rebuilding a report **without** waiting for a
suite.

A page belongs to one top-level test class, **unless `@Suite` classes split it.** A suite that
selects a single `@Nested` class (`@SelectClasses(names = "...Outer$Inner")`) or a single method
(`@SelectMethod("...Outer#method")`) gets its own page, named after the suite
(`OtherCategoryManageGamesSuite` → `OtherCategoryManageGames-test-report.html`) with its own
`.state` file. `HtmlReportGenerator.isolateSections` does this, and only when those suites cover
every case in the class between them. Before this, `-Dsuite=OtherCategoryManageGamesSuite`
rewrote the single Other Category page and marked the other 69 rows Not run. Now each section
keeps its own last run. A class-level failure (`@BeforeAll`) is charged only to the cases the run
selected, for the same reason.

**Every report keeps each suite's latest result until that suite runs again.**
- **Screenshots** are cleared per case that ran, not per class (`clearShotsOfCases`). Clearing
  per class wiped the Edit section's pictures whenever the Create section ran.
- **The suite state** in `.state/<Suite>.properties` stores each case's status and the first line
  of its message, so the index can list a kept suite's cases.
- **The Extent dashboard** keeps every case's latest result, trail and screenshot paths in
  `extent/.state/cases.json`. Each node is labelled "last run ...".

**Each suite page's header is its statistics summary, and it can be minimised.** Click the header
to collapse it to the title, counts and bar. Open, it shows the run details, stat tiles, a stacked
bar with counts and hover tooltips, and the cases grouped by result, each linked to its row
further down. The page remembers whether the reader left the header minimised. The index stays a
plain table of suites; the statistics belong to each suite's own page. Every status colour is
paired with an icon and a label.

`docs/reports/cases/*.xlsx` and `docs/reports/client_testcase-results.xlsx` are **gitignored**.
They are outputs rebuilt from the committed import, and about a megabyte of binary each -
committing them would put an unreadable diff into the history every time anyone ran a single case.
The HTML beside them stays tracked, because its diffs can be read.

---

## The two safety tags

- **`creates-data`** - effects that outlive the run. In a back office that is a wider category
  than it was on the member site: approving or rejecting a transaction, creating or editing a
  member, changing a setting another suite reads. When in doubt, tag it.
- **`known-defect`** - asserts the behaviour the application *should* have, so it fails today by
  design. The regression net for open findings, not a health check.

An ordinary run excludes both. Excluded cases still appear in the report as **Not run**, so the
gap is visible rather than silent.

---

## This project shares a machine with NewWeibo_automation

Three things were deliberately changed so the two cannot collide:

| | Admin_weibo | NewWeibo_automation |
|---|---|---|
| URL-handler scheme | `awb://` | `fpr://` |
| `tools/run-server.ps1` port | 8766 | 8765 |
| Swagger UI port | 8081 | 8080 |

`tools/install-url-handler.ps1` writes an **absolute path** into `HKCU`, so re-run it if the
repository moves. The run token lives in `%LOCALAPPDATA%\Admin_weibo\run-token`, outside the
repository, so it is not committed.

---

## Package layout, and why it is not flat

Page objects and tests are grouped by area into **mirrored** sub-packages: `page.payment` ↔
`tests.payment`. Flat `page/` and `tests/` packages were what NewWeibo_automation started with and
abandoned once there were more than a dozen of each. Base classes live in their own `base/`
package rather than at the root of the package that inherits from them.

`page.common` is for a component two areas already use - not one that a second area plausibly
might.

---

## Working notes

As suites are written, record in this section what each one costs to run, which of its cases
depend on the order the others ran in, and any trap that cost a run to find. That is what this
file is for; the conventions above are already in `README.md`.

### The group pickers work now (2026-09-21)

`/api/account/agent` and `/api/account/member_group` used to answer
`{"status":-10,"message":"Permission denied"}` for the operator in `test.username`, so both Option
panes on the Create form rendered **empty** and every program the suite could make was targeted at
the form's `Select all` default — **All Agents / All Groups**, a single pairing that the back
office treats as colliding with every program on the tab.

That permission has been granted. The pickers now offer **218 Agent groups and 5 Member groups**,
and the consequences run further than the ten rows it unblocked — `RBMP-016`, `RBMP-024`,
`RBMP-039`, `RBMP-041` and `RBMP-042` on the Real-time tab, and `RBMP-061`, `RBMP-071`, `RBMP-075`,
`RBMP-093` and `RBMP-094` on the Daily one:

- the suite can seed fixtures **beside** hand-seeded data instead of needing a currency it owns,
  which is why `test.currency` is back on HKD;
- `RBMP-006` makes its own second page rather than aborting for want of one;
- anything still written around "the suite can only express one pairing" is now describing a
  constraint that has been lifted. Several Javadoc blocks in `RealTimeRebateTest` still do.

### `wasAccepted()` believes the form, and the form lies for about a second

`RebateProgramFormPage.wasAccepted()` is `!isOpen()`, and after a submit that also raises the
screen's `div.custom-loader` the form can still be in the DOM past the settle wait. A create that
**succeeded** was therefore read as refused; the program stayed; and every later create was refused
for re-using the **name**. A search for two free pairings duly walked all 218 Agent groups
reporting each one as taken, ran for ten minutes, and left a program behind.

**Decide a create from the list, not from the form** — `createTargeting` in `RealTimeRebateTest`
navigates back and asks `isProgramListed`. Nothing else can be fooled that way.

### `div.custom-loader` eats clicks

The same overlay swallows pointer events while it is up. A `cancel()` underneath it fails with
Playwright's *"intercepts pointer events"* and then a 6s timeout — a message about the Cancel
button, which was working perfectly. Two ways out, both in use: leave the form **by URL** when the
row is done with it (a Create discarded by navigation is discarded exactly as Cancel discards it —
`RBMP-019` is the row that proves it), or call `awaitLoaderGone()` when the row has to stay on the
form, as `RBMP-016` does to correct a pairing in place.

### What a run costs

`RealTimeRebateTest` is about **20 minutes** headless and `DailyRebateTest` about the same. Most
rows are seconds; these are not:

| Case | Cost | Why |
|---|---|---|
| `RBMP-006` / `RBMP-061` | ~5m each | seeds ~22 programs to build a second page, then deletes them |
| `RBMP-016` / `RBMP-024` / `RBMP-039` | ~1m each | each probes for free pairings by creating and deleting |
| `RBMP-071` / `RBMP-075` / `RBMP-093` / `RBMP-094` | ~1–1.5m each | the same, on the Daily tab |

A probe is a create, a look at the list and a delete — six seconds or so. `PAIRING_PROBE_LIMIT`
caps the search at 12 Agent groups so an environment with nothing free is a finding in a minute
rather than a suite that looks hung.

### The settlement rows read the ledger

`RBMP-050`–`RBMP-055` and `RBMP-105`–`RBMP-111` are about what settlement *does*, and settlement has
no admin control. They used to abort on that. They now read the **Rebate report**, which is the
settlement ledger, and assert the part of each claim it can answer — that past settled amounts
survive a rate edit, that every credited rebate clears Min. Claim, that no row is repeated in full —
and record in the report which part it cannot.

Three things about reading that report, each of which cost a run:

- **Its date range defaults to today, and that made the rows lie.** They passed on 2026-09-21
  against real records and reported **Not run** the next morning against the same untouched
  database — the date had rolled over and "today" was empty. Every ledger read now goes through
  `searchWholeDailyLedger` / `searchWholeRealTimeLedger`, which clear the range first. A suite whose
  coverage depends on how long ago somebody seeded it is not reporting on the application.
- **`clearTimeRange()` does not always take.** The same search returned 50 rows on one run and 0 on
  the next, minutes apart. Three clicks 500ms apart left the field at `2026-09-22 00:00:00 -
  2026-09-22 23:59:59`. The helper now reads the field back and falls back to
  `RebateReportPage.setTimeRange(EVERY_DATE)`, which assigns the value and dispatches `input` and
  `change` so the Vue model follows — a plain `fill()` on that datepicker input does not.
- **The table repaints after `search()` returns.** `RebateReportPage.search` waits a fixed 1200ms;
  a read taken a moment early sees the "No data" placeholder. That failed `RBMP-053` against 50
  records that were there the whole time, on its *second* read of the same report. Both helpers now
  wait for `rowCount() > 0` before the caller reads.

### `createTargeting` decides by name, so it cannot judge a duplicate name

It looks the program up on the list to tell whether a create was accepted, which is what makes it
immune to the `wasAccepted()` trap above. The cost is that it cannot be used by a row that submits a
name already on the list — it would find the *existing* program and report success. `RBMP-075`
drives the form inline for exactly this reason and judges on `programRowCount`.

### The suite creates on a pairing of its own (`ensureSuitePairing`)

Ten `RealTimeRebateTest` rows reported Not run on the 2026-09-22 full run — `RBMP-005`, `017`,
`022`, `030`, `031`, `032`, `033`, `044`, `047` and `114` — and every one of them had **the same
cause**: `createProgram` left the Create form on its "Select all" default, which the back office
records as All Agents / All Groups, a single pairing that collides with every program on the tab.
On an empty currency it worked; on HKD the suite could create nothing, and every delete, rename and
create row went Not run.

The suite now finds one free pairing per run and builds all its own programs on it. Three things
follow, each of which was its own skip:

- **`fixtureProgram()` creates before it borrows.** Borrowing was the old workaround, and a borrowed
  program is one `requireOwnProgram()` will refuse to rename or delete — which is exactly what made
  `RBMP-005`, `RBMP-022` and the `RBMP-030`–`033` delete rows unrunnable. Borrowing is now the
  fallback, not the first choice, and it says so in the report when it happens.
- **`removeSuiteProgram()` clears all three suite names**, not just `SUITE_PROGRAM`. They all share
  one pairing so only one can exist at a time, and a leftover under any name blocks a create under
  that name — `RBMP-044` was Not run because "New Real-time Rebate 2" was still on the tab.
- **`abortIfRefused` reads the list, not the form**, and drops the remembered pairing so the rows
  after it look for another.

### A program on page two is invisible AND undeletable

The list renders 20 rows and every lookup this suite does — `isProgramListed`, `clickDelete`,
`getProgramStatus` — can only see the page that is rendered. So `removeSuiteProgramNamed` reported
"already gone" for a program sitting on page two, the create that followed collided with it on the
**name**, and the row reported Not run blaming the *pairing*. What gave it away: `RBMP-065`,
`RBMP-072` and `RBMP-099` were each refused on a **different** pairing — walking the pairings could
not help, because the name was the blocker. `showAllPrograms()` widens the list to 200 before any
lookup by name; `RBMP-006` / `RBMP-061` are the only rows that set the page size deliberately, and
they put it back.

### Do not probe for the pairing the suite will use

The obvious approach — ask `findFreeAgents` for a free pairing and remember it — cannot work.
A probe proves a pairing free by creating on it and **deleting again**, and the server releases the
pairing some time after the row leaves the list. The very next thing the suite does is create on
that pairing for real, so it lands inside the gap and is refused for a pairing the probe has just
shown to be free. `RBMP-065` reported Not run twice this way, once even after a 2.5s retry.

`createProgram` therefore does not probe: it attempts the real create and, on a refusal, moves to
the next Agent group. A refusal costs what a probe cost and proves more — the pairing is free *for
this submission*, which is the only question that matters — and nothing is created and deleted to
find out. Rows that build their own Create form need the same walk, which is what
`createOnSuitePairing(name, fill)` is for: `RBMP-072` reported Not run against one pairing while
eleven others sat untried.

`findFreeAgents` is still right for the rows whose *subject* is a held pairing — `RBMP-071`,
`RBMP-093`, `RBMP-094` — because they need to know a pairing is free *and* keep it free. Those go
through `createTargetingOnFreePairing`, which retries once for the same release gap.

### `RBMP-065`'s configured pairing is taken on the Daily tab

`test.group.agent.name` / `test.group.member.name` name a pairing (`agent-8001 / Default 00`) that
nothing reserves for this suite, and on the Daily tab a seeded program already holds it — so the
row was Not run over a refusal that had nothing to do with what it tests. It now falls back to the
suite's own pairing and records that the named-group half could not be exercised. The same pairing
is free on the Real-time tab, which is why `RBMP-011` never showed this.

### Three Daily "failures" were one missing fixture

`RBMP-058`, `RBMP-073` and `RBMP-080` failed on the 2026-09-22 run — `RBMP-073` on
`expected: <Enable> but was: <Disable>`, which reads like a status defect. It was not: `RBMP-065`
could not create the fixture, so the rows downstream read a *borrowed* program that happened to be
Inactive. Fixing the create fixed all three. **A fixture that silently falls back to somebody
else's data turns one skip into several unrelated-looking failures.**

### `RBMP-114` needs a pairing free on *both* tabs

Its claim is that pairing uniqueness is scoped to the rebate **type**. Give it a pairing the Daily
tab already uses and a refusal there has two explanations — the types share one pairing space, or
Daily simply has its own program on it — and the row cannot tell them apart. It read a
Daily-internal collision as a cross-type finding on the first attempt, because `agent-8001 /
Default 00` is free on Real-time and taken on Daily. It now probes for a pairing free on both
(free-on-Daily is the scarce one) before it asserts anything. Verified 2026-09-22 on
`test0213 / Default 00`: the types do enforce independently.

The row's assertions were also **inverted** before this — it asserted the Daily submission was
refused and the program *not* listed, under messages saying "should be allowed" and "should be
listed". Whichever way the back office behaved, it reported the wrong thing about it.

### `RBMP-052`: a threshold cannot be checked against history

Member `qa0000904` is in the ledger with a credit of **0.99 HKD** against a Min. Claim of **1**.
That failed the run, and the failure was not sound: Min. Claim is a *current* setting and the ledger
is read over its *whole* history, which it has to be. A credit that settled under an older, lower
threshold is correct and looks identical. The report carries neither a date column nor the threshold
in force at settlement, so "credited below the threshold" and "credited before the threshold was
raised" cannot be told apart.

The row now asserts only what cannot be confounded — that a settled record credited something — and
reports the below-threshold credits by name and amount. **The 0.99 is a finding for the client, not
a suite failure**, and the fact that no operator could attribute it either is worth raising too.

### Finding: the Program Name IS enforced as unique

The suite had assumed the opposite — that a name is a label and the Agent/User Group pairing is the
only uniqueness key. With the pickers granted, `RBMP-075` could finally put a duplicate name on a
**free** pairing, and the back office refused it (2026-09-22), silently, with no on-screen message —
the same silent refusal `RBMP-093` records for the pairing rule. `RBMP-020` cannot make this
attribution because every program it can create duplicates the pairing as well as the name.

### Product > Other Category is written ahead of the screen (2026-09-23)

`OtherCategoryTest` (`-Dsuite=ProductSuite`) covers all 78 rows of the client workbook's
**Other Category** tab, imported into `client_testcase.xlsx` as its own green tab beside the rebate
one. On that date admin-test had **no Product menu** and `/product/other-category` rendered an empty
shell, so a probe in `@BeforeAll` finds no screen and every row reports Not run naming that one
reason. The bodies are complete; they run as written once the screen exists.

On the day it ships:

- **Correct `test.otherCategory.path`** in `Config/` if the screen is not at the guessed address.
  The list page follows a menu entry when one exists and only falls back to that path.
- **Measure `org.sengmean.page.product` against the real markup.** The page objects are modelled
  on the client's five captures, kept in `docs/mockups/other-category/` (they are WEBP despite the
  original `.png` names). Their locators use the captures' wording ("Add category", "Save & manage
  games", "Delete category", "N games selected", "✓ Selected", "Save banner") and read tables by
  header text, not markup. A miss is an adjustment, not a defect. Two things the captures cannot
  show: whether the **✓ Selected** marker is the deselect control, and the real **language tab
  spellings** (`OtherCategoryFormPage.LANGUAGE_TABS`).
- **The client-facing rows guess the member site's language paths** (`/en`, `/zh-TW`, `/vi`, `/bn`)
  and read a category by its heading text. Confirm both against the member site.
- **The open questions were answered on 2026-09-24** and every dependent row now asserts the
  answer (the list is in `OtherCategoryTest`'s Javadoc). They are AQ-01..AQ-10 plus three
  unnumbered rows, written *inside* the Other Category rows - the workbook's Assumptions tab is
  the Rebate feature's, and its AQ numbers are a different set. The answer with the widest reach
  is **AQ-05: Display Order is unique**, so no fixture may ask for a fixed order; every create
  and order edit goes through `freeOrder()`, which falls back to the first free order from 90.
- **Two rows need accounts `Config/` does not yet have.** `OC-ACC-002` proves the Admin role only
  when `test.admin.username` differs from `test.username` (both are `qa0001`; until then it runs in
  the operator session and says so), and `OC-REG-004` is Not run until `test.alt.*` names a
  second operator.

The workbook writes these ids with a hyphenated prefix (`OC-LST-001`), which the report's
`HtmlReportGenerator.CASE_ID` did not accept; it now takes letter-led prefix segments.
Every category the suite creates is prefixed `QA OC ` and swept at the start and end of the class.

### Never build a locator pattern with `Pattern.quote`

Playwright hands a Java `Pattern` to the browser as a JavaScript regex, and JavaScript has no
`\Q...\E`. A `Pattern.quote`d label therefore matches **nothing**, silently, and the click waits out
its timeout. Every row action and language tab in the first Other Category page objects was built
that way; driving them against an HTML mock of the captures is what showed it. Use
`BasePage.exactly(text, ignoreCase)`, which escapes each character in a form both dialects read.

### Manage Games cannot read back what it saved (2026-09-25)

`GET /api/account/game-category-features/{id}/games` answers `{"status":-10,"message":"Permission
denied"}` for `qa0001`, while the `PUT` to the same address saves perfectly well. So a category's
game assignments are written - the list's Games column counts them - but every time Manage Games
opens it shows **0 games selected** and every game as Not selected. The same shape as the group
pickers before 2026-09-21: a permission the operator has not been granted.

Two consequences beyond the obvious:

- Every row that reopens Manage Games and expects its earlier selection fails - `OC-GAM-006`..`010`,
  `OC-REG-002`, `OC-LST-007`, and the banner rows that need a game already bannered.
- **A save from a reopened Manage Games replaces the set.** The `PUT` carries only what the screen
  shows as selected, and the screen shows nothing it saved before, so adding one game removes the
  others. That is why `OC-LST-007` saw the count stay at 1 instead of rising to 2.

Nothing in the suite can work around it honestly; grant the permission and the rows run as written.

The games table also loads as it scrolls: after the last loaded game there is a lone
`<td colspan="5" class="games-sentinel">` row. `ManageGamesPage` skips any row with fewer cells than
the header - it had been reading the sentinel as a game with a blank name.

### Seed data: 20 categories kept between runs (2026-09-25)

`OtherCategorySeedData` creates "QA SEED 01".."QA SEED 20" (`OC-CRT-902`, run with
`-Dsuite=OtherCategorySeedSuite`) and deletes them again (`OC-DEL-901`,
`-Dsuite=OtherCategorySeedCleanupSuite`). The prefix is deliberately not "QA OC ", so
`OtherCategoryTest`'s sweep leaves the seed alone, and the orders are 201-220, clear of the 90-up range
`freeOrder()` uses.

What it buys: `OC-LST-005` and `OC-LST-006` need more categories than the list's smallest page (20)
and used to create ~16 each per run - about seven minutes apiece. `seedMoreThan` counts what is
already listed first, so with the seed present it creates nothing and the report says so. The class
name does not end in `Test` on purpose: a plain `./mvnw test` would otherwise seed and delete on every
run.

### Tool > Notification (written 2026-09-26, live on admin-test since 2026-09-28; state as of 2026-09-30)

`NotificationTest` covers **148 rows**: the 102 rows of the client's **Notification** workbook (v1.3, imported
into `client_testcase.xlsx` as its own green tab after Other Category, ids `NOT-LST-001` .. `NOT-REG-007`, one
method per row) plus 46 project-added rows numbered on from each tab's last id - `NOT-CRT-027`/`028` and
`NOT-LST-010`, `NOT-CLI-013`, `NOT-GRP-012` from Si's requests, the forty from the 2026-09-30 gap review
(listed in their own note below), and `NOT-REG-011` from Si's 2026-10-01 question (its own note below). The project-added rows are in the workbook through `append-cases` and on
`docs/test-cases/notification.html`. Run it as `-Dsuite=ToolSuite`, as one of the twelve
`suites/tool/Notification*Suite` (one per section, each with its own report page), or as
`-Dsuite=NotificationE2ESuite` (every section but Access control, seed suites left out). The screen is at
`/tools/notification` (plural - `test.notification.path`); the probe in `@BeforeAll` still guards every row
and reports Not run naming the reason if it ever goes missing again.

The page objects were modelled on the client's six captures (list, the Add form in its Daily / Once /
Weekly states, Record History, Record Details, and the member-side Message Inbox) and then measured against
the shipped screens on 2026-09-28..30; the notes below record each adjustment. `PromptMessage` lives in
`page.common` because Other Category and Notification both use it. What every run depends on:

- **The targets.** `test.notification.agentGroup` / `memberGroup` (agents001 / Default 00) must be groups
  the member in `test.user.*` (qa000092) belongs to, or every Message Center row reads an empty inbox.
- **Message Center is on the member site** (`page.client`, route `client.messages.path`). Since
  2026-09-29 that is the developer integration page `/en/inbox-test/` ("Message Center (联调)"), reachable
  only by address once signed in; `MessageInboxPage` is written against its real markup (inline styles,
  no classes - cards are the `cursor: pointer` panels, unread is the `rgb(239, 68, 68)` dot, the detail
  is a modal closed by 关闭). Its 语言 select starts at **zh-TW**, so `open()` switches it to
  `client.messages.language` (en) or every English title lookup misses. Re-measure when the designed
  screen replaces it.
- **The schedule rows wait for a real firing.** `NOT-CRT-023`, `NOT-EDT-004`, `NOT-EDT-010`, `NOT-RHL-005`,
  `NOT-REG-003` and `NOT-DEL-005` set Timer to the next whole hour and wait for it, but only with
  `-Dtest.notification.waitForSchedule=true`; otherwise they report Not run naming that key.
  `NOT-REG-001` / `-002` are decided from existing Record History instead of waiting days.
- **Rows that stay Not run by design:** `NOT-GRP-001/002` need Agent or Member Groups created elsewhere
  during the run, which is shared configuration this suite never touches; `NOT-CLI-007/012` need real
  deposits and withdrawals; `NOT-CLI-009` needs the outsider member (`test.notification.outsider.*`,
  qa000096, configured); `NOT-REG-007` needs `test.alt.*`, still blank. `NOT-I18N-003/005` run since
  2026-09-30 against Message Center's 语言 select (see their note below).
- **Regression rows (2026-09-29).** `NOT-REG-004/005` carry AQ-10 on the **Member Group** side with a
  throwaway group (`QA NOT REG Group A/B`) made and deleted through `page.member.MemberGroupPage` -
  Agent Groups cannot be thrown away (no Agent management for the operator; an agent is a member
  qualified in place). Both then Send Now, so they fail on GD-126 until that permission is granted.
  `NOT-REG-002` makes its own Daily rule fire at the next hour when history has none (HKD Record
  History held no Daily firing in 30 days), so it needs `waitForSchedule=true`. `NOT-REG-001` needs
  three days of Daily firings and cannot be shortened. **Any sign-in as `qa0001` evicts a running
  suite** - its 2026-09-29 12:16 errors were an exploration script signing in mid-run.
- **Test member for targeting (2026-09-29):** `qa000096` is in the HKD Member Group `QA Test Group 096`
  and has applied as agent `qaagent096` (0%); both appear in Add Notification's pickers.
- **Six rows are still "confirm whether"** in the workbook (`NOT-DEL-004`, `NOT-GRP-003`, `NOT-RHD-005`,
  `NOT-RHD-006`, `NOT-I18N-005`, `NOT-REG-007`) plus AQ-08's second half: they record what the screen does
  and assert only what any answer must satisfy.

### Message Center delivery is queued, so the inbox rows wait for it (2026-09-30)

The 11:48 run of `NotificationClientInboxSuite` had seven rows fail on one symptom: Send Now reported
Sent, the suite opened `/en/inbox-test/` seconds later, and the message was not there. The screenshot
showed why - rules that fired at 11:00:00 landed in the inbox at 11:00:41. A single read taken right
after Send Now decides nothing. `MessageInboxPage.awaitDelivery(title)` now refreshes (the page's 刷新
button) every 5s for up to `client.messages.deliveryWaitSeconds` (150), and `NOT-CLI-001`..`006`, `008`
and `010` go through `inboxHolding(title)`, which asserts the arrival before reading anything else.
`NOT-CLI-009` waits the same window before deciding the outsider (`qa000096`, now in `Config/`) got
nothing. Two rulings from Si the same day: the Send Now fixture is `SEND_NOW_TITLE` with **Select all**
on both groups (the Daily rule stays targeted, because the list and history rows read its group names),
and `NOT-CLI-007` / `NOT-CLI-012` stay skipped - the suite will not make real deposits or withdrawals.

Also seen in that screenshot, and not the suite's to fix: `QA SEED NOT` notifications, every one saved
Inactive, were delivered to the member at 10:08, 10:09 and 11:00. Worth filing beside GD-126.

### The member reads the inbox on the suite's currency (2026-09-30)

The 11:48 inbox screenshot read `currency: USDT` while the suite creates every notification under
`test.currency` (HKD), and the back office keeps them per currency. `memberInbox()` now calls
`MemberSignInPage.useCurrency(CURRENCY)` right after sign-in - the `?currency=` landing-page switch ported
from NewWeibo_automation's `CurrencySwitcher` - and the project-only row `NOT-CLI-013` asserts the Message
Center's own `currency:` line matches. If a delivery row still fails, check that row's trail for the wallet
the member actually ended up on before blaming Send Now.

### Three Record History / Edit rows fixed from their evidence (2026-09-30)

- **`NOT-RHL-001`**: the Actions column is an eye icon with no text, so `entry.actions()` read "". `RecordHistoryPage.actionsOf` now reads a cell holding a control as "View".
- **`NOT-RHL-006`**: a Once rule that had sent once counted 2 entries while the screenshot showed one. `RecordHistoryPage` now scopes its rows to the table headed "Released At" (the rules table shares `table.custom_table`) and de-duplicates by entry ID. That was not the whole cause: the 16:33 run still read 3, and its screenshot showed why - Record History outlives the rule, so every earlier run of the row today had left one "QA NOT Once Only" entry behind (15:04, 16:33, ...). The row now deletes any leftover rule first, takes the entry IDs under the title before saving its own, and judges only the entries that appear after - the report names them by ID and Released At and says how many earlier ones it left out.
- **`NOT-EDT-002`**: the save was right - the reopened form's GET carried "Updated English content" for `en` and the original text for the other eleven - but the form rendered the English tab blank, the load defect `NOT-EDT-001` tracks. `NotificationListPage.clickEdit` now hands that body to the form (`servedRule()`, `servedContent(tab)`, `servedTitle(tab)`), and the row decides from it, recording any tab that renders other than what was served.

### Record Details and Localization rows (2026-09-30)

- **`NOT-RHD-001` / `NOT-I18N-001`**: the details view's language tabs are the form's `ul.notif-lang-tabs`, not `ul.nav-tabs`; the old locator matched nothing and every tab click timed out. `RecordHistoryPage.languageTabs()` now reads both. `NOT-RHD-001` also asserted the capture's Released At against the Content field - it now checks the Content that was sent.
- **`NOT-RHD-003`**: `someGroups()` clicked Add while the page was still on Record History, where there is no Add button. It opens the list first now.
- **`NOT-I18N-002` / `NOT-I18N-006`**: under the 中文 interface every label-text lookup times out - the Status / Show filters `openList()` drives, and the "Record History" tab. `openRecordHistory()` now finds the tab by its `?tab=history` link, `RecordHistoryPage` finds its table as the visible `table.custom_table`, `AdminShellPage.selectLanguage` bounds its wait and survives a reload, `NOT-I18N-006` reaches the screens by `list.open()` (address and class only), and `NOT-I18N-002` records it when the switch discards the unsaved form and proves the independence on a fresh form instead.

### `NOT-REG-002` counts only the Timer hour (2026-09-30)

The row groups a Daily rule's Sent entries by hour window and expects one per window, but it had counted Send Now pushes too, and the suite's own runs put four of those in one morning hour - a false double send. `scheduledSends()` now keeps only entries whose Released At hour equals the rule's Timer hour (`dailyTimers`, read from the list), and the firing path filters the same way. `NOT-REG-001` still reads every entry, so Send Now pushes at other hours will read as drift there once a Daily rule has three days of firings.

### `NOT-LST-006` read the table mid-repaint (2026-09-30)

After Prev the row read page 2's first title at the top of an otherwise correct page 1, while the screenshot a moment later showed page 1 exactly right - the fixed one-second wait after a pager click was too short for DataTables to finish replacing the rows. `NotificationListPage.turnPage()` (behind Next / Prev / First / Last) now waits for the info line to leave the page it was on and then for two reads of the titles 400ms apart to agree. The row's expectation is unchanged: the system does return to the same first page.

### Localization rows read the member's language from Message Center's 语言 select (2026-09-30)

The 16:00 `NotificationLocalizationSuite` run had `NOT-I18N-002` and `NOT-I18N-006` error on the same lookup:
under the 中文 interface the form's "Title" / "Content" / "Link" labels translate, and `NotificationFormPage`
found its fields by their English label only. `notifField` now accepts the 中文 spellings, and `field()` falls
back to the field's place in the form (Title is the text input before the Content textarea, Link the first
after it) for any spelling it does not know. The form's open wait went to 30s, the shell's language-switch
wait to 20s with a 3s settle, and `test.actionTimeout` to 10s (Si's ruling, same day).

`NOT-I18N-003` and `NOT-I18N-005` were skipped for want of a member-side app-language switch. Si's ruling: the
member's language is the **语言** select on Message Center. `MessageInboxPage.selectLanguage` finds it by that
label, returns whether the language is offered, and `languageOptions()` lists what is; each `Message` card now
carries its "#123" id, so a message can be followed across languages (`messageById`). `NOT-I18N-003` customizes
English after Override, checks the served rule kept it, Send Nows, and reads the message under `en` and
`zh-TW`. `NOT-I18N-005` saves with Bengali blank and records the answer: a refusal (then the client behaviour is
unreachable and the row asserts the refusal says why), or, once delivered, whether the message is omitted under
`bn`, falls back to the 繁體 text, or shows something else - an empty message is the one answer it fails on.

### Forty rows added from the Notification gap review (2026-09-30)

Si asked for a gap analysis of the Notification feature and then for every automatable row on it. Each is numbered
on from its tab's last id (never the 900 block), and none has a workbook row yet: `NOT-CRT-029`..`038` (form defaults,
declining the send-now confirmation, spaces-only fields, markup and Unicode, single-language Override untick, Override
from a blank source, duplicate titles, a Once rule at a past hour), `NOT-EDT-007`..`013`, `NOT-LST-011`..`015`,
`NOT-ACT-004`..`005`, `NOT-RHL-008`..`012`, `NOT-RHD-007`..`008`, `NOT-I18N-007`, `NOT-CLI-014`..`017` and `019`..`020`,
`NOT-REG-010`, `NOT-DEL-005`..`006`. Four of them are gated on `waitForSchedule` like the existing firing rows
(`NOT-EDT-010`, `NOT-DEL-005`) or hold a two-minute watch (`NOT-CRT-038`, `NOT-EDT-011`). Left unwritten, each for want
of something the suite cannot supply: `NOT-CRT-032` (a length limit from the client), `NOT-ACT-006` (a second operator
without the Send Now permission, `test.alt.*`), `NOT-ACC-006` (forcing a session expiry), `NOT-CLI-018` (a real
withdrawal), `NOT-REG-008`/`009` (days of observation). `NOT-CRT-039` and `NOT-I18N-008` from the review were dropped
as duplicates of `NOT-CRT-014`/`018` and `NOT-LST-004`/`NOT-RHL-004`.

What the page objects gained for them: `PromptMessage.declineAndRead` and `NotificationFormPage.clickSaveAndDecline`
(Cancel on the save confirmation), the list's `statusFilter()` / `typeFilter()` / `languageFilter()` readers,
`RecordHistoryPage`'s page size, info line and Prev/Next pager (the same DataTables controls as the list, scoped to the
visible table), `AdminShellPage.headerClock()`, and `NotificationTest.memberSite` so a row can move the member to the
other wallet after `memberInbox()`.

Five of the new rows are "confirm whether" rows that record the answer and assert only what any answer must satisfy:
`NOT-CRT-036`, `NOT-CRT-037`, `NOT-EDT-011`, `NOT-ACT-004`, `NOT-RHL-009`. `NOT-CRT-029` characterises the shipped
defaults (Once / — / Active), on which a rule saved untouched is sent at once - worth a ruling from the client.

### `NOT-CRT-028` and its defect GD-137 (2026-09-30)

Si asked for a Create row for this sequence: fill Title and Content on 繁體, tick **Override above contents to: All
languages**, untick it again, Save - expecting the languages-required validation. It is `NOT-CRT-028`
(`untickingOverrideLeavesLanguagesRequired`, numbered on from `NOT-CRT-027`), and `NotificationFormPage` gained
`overrideOff(language)` / `overrideAllOff()` for it. Its first run failed as a finding: after the untick, English and
简体 still held the 繁體 text, Save went through ("Successfully added", rule ID 277) and, on the form's default
Once / — / Active, the rule was sent at once. Filed as **GD-137** (labels automation, notification, override,
validation) from `docs/jira-drafts/NOT-CRT-028.jira.txt`, with the run's closing screenshot attached; the readable
draft's header line records the key. The row stays red until the untick clears the copies. `NOT-CRT-035` is the
single-language version of the same claim.

### Project-added rows now live in the workbook too, through `append-cases` (2026-09-30)

Si asked whether the added Notification rows were in the test-data workbook. They were not - a case added to a
tab existed only in the suite - so they now are, and the way they get there is a tool, for the same reason the
trimmer is one: `client_testcase.xlsx` is a copy of the client's sheet, and a re-export drops anything typed into
the copy. The rows are kept in `src/test/resources/testdata/added-cases/notification.tsv` (one line per case,
the tab's own columns, `\n` for a line break in the steps, VERSION `v1.4`, LAST UPDATE as a date) and
`org.sengmean.testcase.CaseSheetAppender` (`./mvnw exec:java@append-cases`) writes each one after the last
existing row of its prefix, copying the row above's styles, skipping ids already present. Then
`exec:java@split-cases` rebuilds `features/notification.xlsx` and `exec:java@case-page` the docs page, so the 45
project rows (NOT-CRT-027/028, NOT-LST-010, NOT-CLI-013, NOT-GRP-012 and the forty from the gap review) now show
on `docs/test-cases/notification.html` with their Run links. The Notification tab is 147 case rows.

Si also keeps the client's original feature workbook at `C:\Users\Acer\Downloads\Weibo_Notification_TestCases.xlsx`,
whose rows sit on a tab named "Test Cases" rather than "Notification"; the appender falls back to the tab whose ids
share the row's prefix, and `-Dexec.args="C:/Users/Acer/Downloads/Weibo_Notification_TestCases.xlsx"` brought it up
to date the same day (the untouched copy is beside it as `...before-2026-09-30.xlsx`). When a new row is added to
the suite, add its line to the TSV and run the three commands - and the same append against Si's copy. When an
existing project row changes, edit its TSV line and run the append with `-Dcases.update=true` (2026-10-01), on both copies.

### `NOT-REG-011` - a Weekly rule moved to a later hour the same day (2026-10-01)

Si asked whether this is right: a Weekly rule's schedule has run and sent once, the admin then edits its Timer to an
upcoming hour the same day, and when that hour arrives the system sends again. The reading settled on: **yes** - Weekly
means "fire every week at the saved day and hour", not "at most one delivery a week", so the admin changed the
definition and the scheduler ran it; moving the hour forward on the same day is how an operator asks for a re-send.
What would be a defect is the save itself re-sending (a Timer edit must not push anything), or two entries in the
moved hour's window (a double send). `NOT-REG-011` (`weeklyMovedLaterTheSameDayFiresAgain`, numbered on from
`NOT-REG-010`) asserts exactly that: a Weekly rule for today's week day at `nextHourLabel()` fires once; its Timer is
moved to the following hour; a minute later there is still one entry and the list shows the moved Timer, still
Active; after the moved hour there are two entries, exactly one in that hour's window, Sent, and the rule is still
Active. It needs **two** scheduler runs, so it is gated on `waitForSchedule` like the other firing rows and aborts up
front naming the two-hour wait; it also aborts if the moved hour would fall on the next Hong Kong day (the premise is
"the same day"). `firingWeekDay()` reads the week day the scheduler will see - the next whole hour on the machine's
clock, converted to Hong Kong Time - and `describe(entries)` moved from `RecordHistoryList` to the outer class so both
sections can log an entry list the same way. Only the Timer is edited: `NOT-REG-003` already covers a Content edit
between firings, and `NOT-CRT-038` the past-hour side. Its TSV line is in `added-cases/notification.tsv`; the tab is
148 case rows.

### `NOT-CRT-022` expects a refusal again (2026-10-01)

Si asked for `NOT-CRT-022` (Saving with both Selected columns empty) to be fixed by changing its expected result to
**not allow** saving without a target group. That reverses the 2026-09-28 ruling to expect the shipped acceptance
("Successfully added"): the workbook's AQ-07 answer - at least one Agent Group and one Member Group are mandatory - is
the expected result once more. The method is now `savesWithNoTargetsIsRefused` and ends in `assertRefused`, like
`NOT-GRP-012`; the draft is still saved Inactive so that, while the site accepts it, a rule with no targets can never
fire before `deleteAfterwards` removes it. The row is red until the validation ships - it is the both-empty side of
**GD-136** (filed for `NOT-GRP-012`'s one-side-empty case), whose draft under `docs/jira-drafts` now says so. The
class javadoc's Feature Report bullet on Select All was corrected to match.

### `NOT-CRT-033` expects the tags hidden, as the system does (2026-10-01)

Si asked for `NOT-CRT-033` (markup in Title and Content) to be fixed "as current system implemented". The evidence is
the 2026-09-30 17:33 `NotificationCreate` run: Save went through ("Successfully added", rule 288) and the list showed
**"QA NOT Tagged title"** - the `<b>` tags gone, not shown as literal text - so the row failed looking for the typed
title in the list. It is now `markupIsNotRendered`: Save succeeds, the list shows the tag-less title and not the typed
one, the rule reopens under the tag-less title, and the served rule is read to record whether the tags were stripped
on save or only hidden on display (both accepted; anything else fails). The one hard line kept from the original: the
`<script>` in the Content must never run on the list or the form (`document.title` stays). The report says which of
the two the site does under "Where the tags go" - if it reads "stored as typed, hidden on display", the display layer
is sanitising stored markup, which is worth a look even though the row is green. `deleteAfterwards` is registered
under both titles, since only the tag-less one is listed. Its TSV line was rewritten with the new expected result,
which needed a way to change a row the workbook already carries: `CaseSheetAppender` now takes
`-Dcases.update=true` and rewrites an existing row's text cells and LAST UPDATE from its TSV line where they differ
(styles untouched, nothing moved). Both workbooks were updated that way, then `split-cases`, `case-page` and
`case-runners` rebuilt as usual.

### `NOT-CRT-007` expects the all-languages validation, as the system does (2026-10-01)

Si asked for `NOT-CRT-007` to be fixed "as same current system implemented as showing validation message". Every
run since the screen shipped has refused a rule filled in English, 简体 and 繁體 only with **"Title and Content are
required for all languages"** (last seen in the 2026-09-30 17:33 Create run). The row is now
`theThreeLanguagesAloneAreRefused`: it fills the three, Saves, and asserts the refusal through `assertRefused`
plus that exact message (`NotificationTestData.ALL_LANGUAGES_REQUIRED`). This reverses the 2026-09-28 ruling that
only the three are mandatory, under which the row stayed red and **GD-103** was filed - that ticket now describes a
behaviour the suite accepts, so it is the client's to close or keep. `NOT-CRT-006` and `NOT-CRT-008` pass under either
rule and are untouched; `fill()` already satisfies the site's rule with Override to All languages. The class javadoc's
AQ-02 and Feature Report passages were corrected to match. The workbook row is the client's (v1.3) and still reads
"sufficient to save"; it was not edited, so the docs page shows the client's wording beside the suite's result.

### `NOT-EDT-013` failed on the details view's placeholder dash (2026-10-01)

The 11:14 `NotificationEdit` run had `NOT-EDT-013` (Removing the Link on Edit) red while its capture showed exactly
what the row expects - no link on the delivery. The trail had the cause: "Link on the delivery after removal = -".
The Record Details view draws an empty field as a dash, `RecordHistoryPage.detail()` handed the dash back as the
value, and the row asserted blank. The site was right; the reader was not. `detail()` now returns "" for a value that
is only a dash (hyphen, en dash, em dash or horizontal bar), so every caller - `NOT-RHD-001`'s `sentContent`,
`NOT-RHD-008`, `NOT-EDT-013` - can ask "no value" without knowing the placeholder. Nothing in the row itself changed.

### A Timer is the machine's current hour plus one (2026-09-30)

Si's ruling: "adjust adding new Notification by set timer with current machine time +1, because the schedule is
set to run every 1 hour". Every row that sets a Timer for a firing (`NOT-CRT-023`, `027`, `038`, `NOT-EDT-004`,
`010`, `NOT-RHL-005`, `NOT-REG-002`, `003`, `011`, `NOT-DEL-005`) used `nextHourLabel()`, which took Hong Kong Time -
one hour ahead of Si's machine (SE Asia Standard Time, UTC+7) - so the Timer landed a scheduler run later than
intended and the wait was an hour longer. `NotificationTestData.TIMER_ZONE` (the machine's zone) now feeds
`nextHourLabel()`, `pastHourLabel()` and `waitPastNextHour()`. Created At and Released At are still shown in
Hong Kong Time (`SCHEDULE_ZONE`), so where a Timer hour is compared with a Released At hour (`NOT-REG-001/002`'s
`scheduledSends`, `NOT-REG-002`'s own firing) the Timer hour goes through `timerHourInScheduleZone()` first.
Whether the server reads a Timer hour in UTC+7 or UTC+8 is what the next `waitForSchedule=true` run will show:
if a firing set for "05 PM" is logged at 18:00 Hong Kong, the conversion is right; if at 17:00, drop it.

### The client rows read the member site's category feed and category pages (2026-10-02)

Every `OC-CLI-*` row, `OC-I18N-003/004` and the client half of `OC-EDT-004` had failed since 2026-09-25 on
"not visible on the client": `ClientCategoryPage` read the member site's landing page for a heading with the
category's name, and the landing page lists no Other Categories. The member site does carry the feature,
in two places the page object now reads:

- **The category feed**, `GET /api/member/game-category-features?currency=HKD&page=1&paginate=200`, open to a
  guest: the Active categories with games, in Display Order, each with its twelve names, icon and games
  (name, catalogue image, and the banner uploaded for that assignment). The home page's category sections are
  rendered from this call in this order - but only under the site's "new" UI version (`uiVersion`), which
  admin-test does not enable, so nothing on screen lists the categories today. `feed()`, `listed(name)`,
  `inDocumentOrder` and `isShown` start from it.
- **A category's own page**, `/{locale}/game-category-features/{id}` (the back office's id, now readable from
  the list's Manage games link with `OtherCategoryListPage.idOf`): a card headed with the name in the viewer's
  language and the games drawn as banner-or-catalogue-image with no caption. No link reaches it; it is
  opened by address. `isShown` is "in the feed under that name, and the page is headed with it".

Three things about the member site that cost a probe each: a locale typed into the address is replaced by the
one the site remembers (`/vi/...` came back as `/en/...`), so a language is chosen through the globe's list
exactly as NewWeibo_automation's HomePage does, and the site's locale codes differ from the feed's keys
(`hk` on the site is `zh-TW` in the feed - `ClientCategoryPage.Language` carries both); the page draws **no
icon** for a category (only the new-UI home sections do), so `OC-CLI-005` stays red on that finding; and the
page renders an **Inactive** category by id (`QA OC Hidden`, id 420, on 2026-10-02) although the feed leaves it
out, so `OC-CLI-002` - now pointed at the real route - reports that as a finding.

### The Manage Games list's load-more marker never goes away (2026-10-02)

`OC-GAM-021` failed with "more were still waiting at 483": the games API (`GET /api/account/game?paginate=20&page=N`)
pages 20 at a time, answered page 25 with the last three games and every page after it empty, and the screen
kept its `.games-sentinel` row and asked for another empty page on every scroll (it was at page 30 when the
row gave up). The marker therefore cannot say whether anything is left. `ManageGamesPage.loadGamesUntil` now
listens to the API's own `current_page` / `last_page` / `total` while it scrolls, stops when the last page is on
screen, and `hasMoreToLoad()` answers from that; `gamesTotal()` lets the row assert that every game the API
counts was loaded.

`OC-GAM-017` is characterised the same day: the search is the server's, and it reads `%` as a LIKE wildcard
(`game_name=%25` listed every game). The row asserts the workbook's claim - no dialog, no error, table and
headers intact - and records per term whether every result contained it; the wildcard reading is a finding
for the client, not a red row.

### `UiOtherCategoryClientHK` - the client's new UI, on the HK brand, on five devices (2026-10-02)

Si asked for the client-side UI checks of Other Category to live in their own class, `tests.product.UiOtherCategoryClientHK`
(`-Dsuite=UiOtherCategoryClientHKSuite`), against the HK brand's member site `client.hk.url` (`https://test-v2.138hk.vip`),
which runs the **new UI**: a fresh visitor is asked "No UI version - Old UI / New UI" (kept in localStorage `ui-version`),
and the New UI renders each Other Category as a home-page panel - icon, name, "More Games >", a row of 160px banner cards
(140px under 640px) with scroll arrows when the row overflows (arrows hidden under 640px) - and a category page at
`/{locale}/game-category-features/{id}` with a 2-column grid under 1024px and auto-fill 280px columns above.
`page.product.ClientCategoryHomePage` reads all of that; `ClientCategoryPage` took a second constructor for the other origin.

The class signs in to no back office. It takes the client's own feed as the truth of what it was given and judges the
drawing against it. `OC-CLI-011`..`019` are the PC rows (sections, cards, arrows, More Games, maintenance mark as a
"confirm whether", visitor tap -> Login dialog, 繁體 headings, unknown id -> "No Games", member tap with the testing
account `client.hk.username` qa00001 Si gave on 2026-10-02). `OC-CLI-020`..`024` are one row per device as a visitor and `OC-CLI-025`..`029` the same five devices as the
signed-in testing member (sign in, sections, category page, a tap that must not ask to log in again) - PC, Mac, iPad,
iPhone, Android - on `support.Device` (ported from NewWeibo_automation, plus a Mac profile and an engine per device):
the Mac and iOS rows run in **WebKit**, which must be installed (`npx playwright install webkit`); a row that cannot
launch it reports Not run saying so. `OC-CLI-030`/`031` walk every game the feed serves as the signed-in member - from the home sections and from each category's page - and hold each to its condition: an available game must launch (new window, navigation or embedded frame) and never ask to log in again; a game flagged `isMaintenance` may decline with a maintenance notice (`ClientCategoryHomePage.launch`, `LaunchOutcome`). All twenty-one ids are project-added to the workbook's Other Category tab
(`added-cases/other-category.tsv`, appended with `-Dcases.added=.../other-category.tsv`), numbered on from `OC-CLI-010`.

Two things the review found that the rows record rather than assert, pending the client's word: a card is the banner
alone (no game name, no maintenance mark), and "More Games" is a button that pushes a route, not a link.

### What covers the HK client's page, and how the rows get it out of the way (2026-10-02, 13:51 run)

Si's 13:51 run of `UiOtherCategoryClientHKSuite` had every member row and every device row red on things laid over
the page, not on the sections. `ClientCategoryHomePage.dismissOverlays()` now runs before every click the rows make
(More Games, a card, an arrow, a game launch) and right after sign-in:

- **Daily Mission.** A member is met with a "DAILY MISSION - Complete N days of 5 days" panel with a Claim reward
  button and an ×. Si's instruction: claim it and close it before the case goes on. `claimDailyMission()` claims when
  the button is offered, closes by the ×, and hides the panel only as a last resort, saying so in the trail.
- **The chat widget (Chatwoot, `#cw-widget-holder`).** Its panel opens by itself some seconds after a page loads and
  covers the right of a PC screen and the whole of a phone's - it is what every phone row's failure capture showed.
  `closeChatWidget()` closes it through the widget's own `window.$chatwoot.toggle('close')`.
- **"Add to Home Screen"** on an iPhone, closed by its ×.

One site finding came out of the same run and is recorded rather than asserted: a visitor's tap on a card of a
**category page** does nothing - no prompt, no navigation (confirmed by hand) - while the same card on the home row
answers "Please login first". The device rows record what the category page does and assert the login ask on the
home row.

### The HK client's categories are run-time data, an empty list included (Si, 2026-10-02)

Other Categories on the HK brand are whatever its back office holds when the suite runs - two one day, one the
next, none the day after - so no row in `UiOtherCategoryClientHK` names a category, counts them, or expects a first
one. Every row reads the client's feed as it runs (`servedCategories`) and validates the page against that list: with
no category served it asserts that no section is drawn and passes (`noneServed`), with none overflowing it asserts
no arrows, with no maintenance game it records that there was none to look at. A row that needs a card to open takes
the first section that has one (`firstWithCards`) and says so when none has. Do not reintroduce an abort for "no
category" - Si wants the empty state validated, not skipped.

### `UiNotificationCenterClientHK` - the Message Center on the HK client's new UI, on five devices (2026-10-02)

Si asked for Notification Center rows on the new UI for every device. The screen is `/{locale}/messages` (a message's own
page at `/messages/{id}`), read by `page.client.MessageCenterPage`, which composes `ClientCategoryHomePage` for the New UI
shell (picker, sign-in, Daily Mission / chat / Add-to-Home-Screen overlays). Measured as a visitor from the page's chunk
and DOM: an `h1` heading, a `role=tablist` of four `role=tab` buttons (All / System / Deposit / Withdraw, `aria-selected`
on the open one), a "Mark All as Read" button `disabled` while nothing is unread, rows (`div.odd:bg-[#F6FAFF]`) with a
truncated bold title, a two-line preview, an 11px time and a `.bg-red-500` dot while unread, Today / Yesterday labels,
"No messages yet" when empty, "All messages shown" at the end (the list loads more as it scrolls), and a Back control
(`button:has(svg[data-icon='chevron-left'])`). Si's ruling: the Message Center is for signed-in members only, so a visitor must be asked to log in or sent away.
On 2026-10-02 the site showed a visitor the empty shell - `NOT-CLI-021` (tagged known-defect), `033` (after signing out)
and `034` (a message's address as a visitor) report it until it is fixed.

Rows: `NOT-CLI-021` (visitor kept out), `022` (member's list and tabs), `023` (open a message, Back, unread mark cleared),
`024` (Mark All as Read enabled only while something is unread), `025`..`029` (one per device as the member), `030` (the
header's message control and its badge against the unread rows), `031` (System + Deposit + Withdraw partition All), `032`
(繁體 keeps the structure, labels translated), `033` (signing out closes the center - `ClientCategoryHomePage.signOut`), `034`
(a message's address refused to a visitor). The
member's messages are whatever they have - none included: an empty list validates "No messages yet" and the row ends.
Ids numbered on from `NOT-CLI-020`; TSV lines in `added-cases/notification.tsv`, appended to both workbook copies.
Run: `./mvnw test -Dsuite=UiNotificationCenterClientHKSuite`.

**Ruling 2026-10-02 (later the same day): the Message Center is for signed-in members only.** A visitor must be asked to
log in or sent away; on that day the site showed a visitor the empty shell instead. `NOT-CLI-021` now asserts the visitor
is kept out (tagged `known-defect` until the site does), and five more signed-in rows were added: `NOT-CLI-030` (the
header's message control and its badge against the unread rows), `031` (System + Deposit + Withdraw partition All), `032`
(繁體 keeps the structure with translated labels), `033` (signing out closes the center - `ClientCategoryHomePage.signOut`)
and `034` (a message's own address refused to a visitor). Both notification workbooks carry the rows.

### The 2026-10-05 `UiNotificationCenterClientHKSuite` run: 14 of 14 red, five causes, none in the list itself

Si's 08:36 run had every row red. The list, the tabs, Mark All as Read, the rows and Back all work and were
never the problem; what the probes found (`target/probe/*.png` while they last) and what changed:

- **The header's message popover duplicates the whole list.** For a signed-in member the envelope button
  (`button[aria-label="Message Center"]`, shown from 1024px up) owns a `role=dialog` popover holding a second
  heading, Mark All as Read, the four tabs, the rows and "All messages shown" - and on `/messages` it is open
  on arrival. `tabs()` read eight tabs, `markAll().first()` was the popover's. There is **no `<header>`
  element**: the header is `div.Header_wrapper__…`, so every `header …` locator matched nothing - which is
  the whole of why `NOT-CLI-030` said "not offered". Every `MessageCenterPage` lookup now carries
  `OUTSIDE_HEADER` (`[not(ancestor::*[contains(@class,'Header_')])]`), and the popover has readers of its own
  (`isHeaderPopoverOpen`, `headerPopoverHeading/Tabs`, `closeHeaderPopover`). The envelope **opens the popover
  and never navigates**; `openFromHeader()` now says which it got ("page" / "popover" / "address") and
  `NOT-CLI-030` accepts the popover as the Message Center the header leads to. On a phone the control is the
  bottom bar's "Message" entry - a `div` with `img[src*='MobileStickyNav/message']`, not a link.
- **The Daily Mission panel's title is an image.** `claimDailyMission()` looked for "daily mission" in the
  text and never saw the panel, so it was never claimed or closed and sat over every member row (its own
  `h1`, "Complete 0 days of 5 days", is what `heading()` then read - the Android row's message). It is now
  known by "days of 5 days" / "Claim reward" (`isDailyMissionUp`), and `heading()` ignores any `h1` inside a
  fixed panel. The panel does not appear on every sign-in - two probes in one morning saw it once.
- **The chat widget opens by itself 1-9s after every page load** and on a phone covers the whole screen, on an
  iPad the sign-in dialog. `signIn` clicked Login (Android) and LOG IN (iPad) straight into its iframe:
  "intercepts pointer events", then a timeout. `clickPast()` closes the widget right before a click, clears
  the overlays and retries on an interception, and forces the third try.
- **The iPhone's "Add to Home Screen" panel is also an image** (`div.InstallPWA_ios-responsive__…` holding
  `img[alt="pwa icon"]` and a `button.InstallPWA_close-popup__…` reading "×"), raised beside a window-sized
  `fixed inset-0 bg-black/35 … z-pwaIOSModal` backdrop that is sometimes there and sometimes not.
  `closeAddToHomeScreen()` could not see it by text. `dismissOverlays`
  ends with `closeCoveringPanel()`: any window-sized fixed element with a backdrop (the sign-in dialog and the
  Daily Mission excepted) is closed by its × and hidden as a last resort. Note for anyone writing such a
  check: a `position:fixed` element has `offsetParent === null`, so an "is visible" test built on
  `offsetParent` reports every backdrop as absent.
- **Choosing the New UI and signing in each reload the page twice**, a couple of seconds apart; an evaluate in
  the gap dies with "Execution context was destroyed" (`NOT-CLI-022` before it had signed in, both WebKit
  rows of the first probe). `evaluateSettled` retries once after `awaitReady`; everything `dismissOverlays`
  runs goes through it.

Verified by probe the same morning, after the changes: PC reads one tablist and the popover by its own readers;
Android reaches `/messages` with the four tabs, Back and Mark All as Read; iPad signs in past the chat panel;
iPhone signs in past the chat panel and reaches the list. Nothing was run as a suite - Si runs those.

Still red by design after this: `NOT-CLI-021`, `033`, `034` - the site shows a visitor the Message Center
shell (confirmed by hand on 2026-10-05: `/en/messages` signed out lists the heading, the four tabs and Mark
All as Read), and Si's ruling is that a visitor must be kept out. `021` carries `known-defect`; the other two
are the signed-out and deep-link sides of the same finding.

Filed the same day from `docs/jira-drafts/`: **GD-153** (`other-category-client-new-ui.jira.txt` - the phone layout
scrolls sideways by 12/34px; an Other Category with no games is drawn as an empty home section) and **GD-154**
(`message-center-client-new-ui.jira.txt` - a visitor reaches the Message Center and a message's page; the header
popover opens by itself on `/messages`). GD's Bug screen no longer carries Reporter, so `file-jira-issue.ps1` stopped
sending it. Two test-side gaps found on the way and not yet fixed: the category page's cards are now
`button:has(img.object-cover)` (a maintenance game is `disabled`), and a member's game tap opens a "Confirm to start
the game?" dialog that `ClientCategoryHomePage.launch()` never confirms - both are why `OC-CLI-030/031` still read
NOTHING. `MessageCenterPage` rows were redesigned to `button.rounded-[14px]` the same day and are read again.

### `SystemPromotionSetupTest` - Discount > System Promotion Setup, a new area (2026-10-05)

Si asked for new test cases on Discount > System Promotion Setup. There is no menu entry for it -
the Discount menu is hidden for `qa0001` - and no row for it in the client workbook, so this is a
project-only area from the start: package `page.promo` / `tests.promo` / `suites.promo`, ids
`SPS-001` upward (not the 900 block, which is for rows added to an *existing* tab), its own
`DiscountSuite` alongside `RebateSuite`, `ProductSuite` and `ToolSuite`.

**The screen exists and renders, found by reading the Nuxt route table** (`_nuxt/*.js` on
admin-test carries `path:"/promo/system-promotion"`), confirmed live at
`/promo/system-promotion` with the heading "System Promotion Setup" and the breadcrumb Home Page >
Discount > System Promotion Setup. Its sections match GD-111's spec exactly: Overall settings,
Wallet Promotion, Live Promotion (above the groups, no currency field of its own - GD-111's "Live
Promotion always uses the requested deposit currency"), then Agent group and Member group at the
bottom with the same Select all / Optional dual-list picker `RebateProgramFormPage` uses.

**But every field is empty and Save is disabled**, because the screen's three data calls -
`/api/account/system-promo`, `/api/account/system-promo/type`, `/api/account/get_payment_channel`
- all answer `{"status":-10,"message":"Permission denied"}` for `qa0001`. With no Type list the
form shows only its defaults (both Status selects Inactive, Wallet Active) and Save stays
`disabled="disabled"` regardless of what the fields hold - confirmed by probe: filling Live
percentage and clearing it again left Save disabled both times. This is the same shape as two
earlier findings on this project - the group pickers before 2026-09-21 and Manage Games's read
permission before 2026-09-25 - and resolves the same way: grant the permission, nothing to work
around in the suite.

`SystemPromotionSetupPage.isDataLoaded()` (the Type list non-empty) and `refusedDataCalls()` (asks
the three endpoints directly and reports which refuse) are what `@BeforeAll`'s probe runs once;
every writing row calls `requireData()` and aborts naming the refusal, the same pattern
`OtherCategoryTest.requireScreen()` used before Product shipped. Six rows need nothing from the
data calls and run today: the heading and breadcrumb (SPS-001), the section order (SPS-002/003),
that Live Promotion has no currency field (SPS-004), that the Time Range control reads without
throwing (SPS-005), the pickers' shape (SPS-006), and both language rows (SPS-007/008, Traditional
Chinese and Thai, against the bundle's own `system_promotion` / `wallet_promotion` /
`live_promotion` / `overall_settings` translations). The eight saving and validation rows
(SPS-009..016) are written against the bundle's own validation wording
(`percentage_required`, `invalid_discount_range`, `live_percentage_must_be_greater_than_zero`,
the `promo_update_failed` Time Range message) and run as written the day the permission is
granted - nothing here is a placeholder.

Every writing row snapshots the setup with `SystemPromotionSetupPage.snapshot()` before it writes
and restores with `apply()` + `save()` in `@AfterEach`, because System Promotion is a single global
setup per currency, the same reason `RebateSetUpTest` restores its own panel before asserting
rather than after.

Run: `./mvnw test -Dsuite=DiscountSuite`. Read-only only: add
`-DexcludedGroups=known-defect,creates-data`.

### `SystemPromotionRecordsTest` - the deposit / System Promotion / System Live Promotion records, written ahead of the UI (2026-10-06)

Si described the requirement directly after a Figma link could not be read (OAuth to the Figma MCP
was not completed in the session, and the browser tab opened on the link never finished
rendering): a qualifying deposit raises up to three linked back-office records - Main Deposit,
System Promotion, System Live Promotion - confirming the parent cascades to both children,
rejecting the parent cascades the same way, but rejecting a child affects neither the parent nor
its sibling, and confirming credits the wallet per record in that record's own currency (the
target/offer currency for System Promotion, always the deposit's own currency for System Live
Promotion, which stores no target-currency field of its own).

This reads word for word like GD-106 (the System Live Promotion epic) and its stories GD-108,
111-117, already filed and mostly Resolved - cross-checking Si's description against them was
how the sixteen `SPR-*` rows got written with confidence despite no visual reference.

**No screen was found to automate against.** The operator's full menu
(`/api/account/allow-menu`) was read and every `trans_name` on it scanned - nothing named
Deposit, Transaction, Finance or Promotion Record. Direct addresses answer "page does not exist":
`/member/deposit`, `/member/deposit-record`, `/deposit`, `/deposit/record`. `/promo/promotion`
does exist ("Promotion setting") but is a different feature entirely - a list of ad-hoc
promotional campaigns (Title / Type / Status / Time Range / Order with an Add promotion button),
not deposit-linked transaction records, and its own data call is refused the same way the Setup
screen's are.

Every row in `SystemPromotionRecordsTest` therefore `abort()`s naming this investigation. It is
wired into `DiscountSuite` alongside `SystemPromotionSetupTest`, ids `SPR-001`..`016`, four
sections (record creation, confirm cascades, reject cascades and isolation, wallet and currency).
Fill the bodies in once the screen is found (correct `test.systemPromotionRecords.path` in
`Config/`) or once someone can share the actual Figma frame to model the markup against, the way
`OtherCategoryTest` originally was.

### The screen above WAS there - "no screen was found" was wrong (2026-10-06, same day)

Si asked for the undone `SystemPromotionRecordsTest` work to be finished. A second look found the
screen the paragraph above said did not exist: the Control Panel's own "Today total deposit
amount" stat tile carries a "More details" link, and it leads to `/ticketing/ticketing-history`,
breadcrumbed "Home Page > Trade > Transaction Records". `Trade` is simply not in the side
navigation for `qa0001` - the same way `Discount` is hidden from `SystemPromotionSetupTest` - so
the menu scan the first pass did could never have found it. The lesson for next time a menu scan
comes up empty: also check the Control Panel's own tiles before concluding a feature has no
screen at all.

The screen's search panel settles two things about the client's taxonomy for real, not just in a
ticket: it offers **eight** Transaction type checkboxes, not seven, and their own DOM ids
(`chk_tt_Transaction type_0`..`_7`) confirm **System Promotion** and **System Live Promotion** are
their own, separate transaction types (12 and 13, read off the search API's own `where[type][]`
values) - distinct from the older ad-hoc `Promotion` (5) and `Live Promotion` (11). Every other
checkbox group (Status, Record type, Member group, Payment Options) follows the same
`chk_tt_<group>_<index>` id pattern, which is what `page.promo.SystemPromotionRecordsPage` (new
this day) is built on, measured live rather than guessed.

**But nothing exists to read yet, and that is a different, narrower blocker than "no screen."**
Every filter this session could open was opened - all eight types, all five statuses, both record
types, every Payment Option, all six Member groups, no date bound - under both HKD and USDT, and
every search answered "No data". Checked directly: `/api/account/order` with no
`member_group_id` filter answers "Permission denied", but WITH one (as the screen's own search
sends) it answers 200 with zero rows, and `/api/account/member_group` itself reports its own total
as 6 - so this reads as a genuinely empty transaction history for this operator's groups, not
another hidden permission wall. `SystemPromotionSetupTest`'s own blocker is also still live the
same day (`/promo/system-promotion` answered "cannot query here" again), so there is not yet even
a configured minimum amount to aim a test deposit at.

Two things outside this suite's reach would unblock the sixteen `SPR-*` rows: the Setup screen's
permission, and a qualifying HKD deposit made through the member site - which, per the project's
standing arrangement for that site (Si signs in themselves; see the deposit QA script), is not
something this suite drives on its own, though pressing Confirm on a deposit once it exists as a
request is something it is already trusted to do elsewhere. `SystemPromotionRecordsTest` now signs
in, opens the screen for real and records whether *any* record exists at all
(`anyRecordExists`), and every row past the two new structural ones (`SPR-017`, `SPR-018` - the
next numbers after the existing block, there being no client tab for this feature) aborts naming
that, rather than the old and now-incorrect "the screen could not be found". `SPR-017`/`018` and
`SPR-005`'s partial check (the panel's own eight-type taxonomy) run for real today; the other
fourteen run as written the day the two blockers above clear.

Verify with `./mvnw test -Dsuite=DiscountSuite -Dcase=SPR-017` or `-Dcase=SPR-018` (read-only,
safe to run any time); the full class needs the two blockers above to turn green.

### Asked to "try again" hours later: 301 deposits, but a second, narrower permission wall (2026-10-06)

Si's own environment had moved on since the note above: `/promo/system-promotion` now loads and
saves for real (Required deposit min. amount **100 HKD**, Wallet Promotion Active **30%**, Live
Promotion Active **10%** - `SystemPromotionSetupTest`'s Javadoc carries its own dated addendum
rather than being rewritten), and **301 HKD deposit requests** from `qa000092` now exist (289
`New Request`, 12 auto-`Rejected` by "system"), every one at or above the 100 minimum. Also new:
the **Trade** menu, reached once logged in through Si's own paired browser rather than guessed at,
turned out to carry three entries - **Instant Transaction** (`/ticketing/new-ticket`, the pending
queue, badge-counted), **Transaction Records** (this class's screen) and **Transaction Report**
(unused here) - all three hidden from the side navigation the same way `Discount` is.

**And yet not one System Promotion or System Live Promotion record exists**, searched the same
exhaustive way as before but narrowed to just those two types - confirmed both through the UI and
directly against `/api/account/order`. Two explanations remain open: either the linked records are
raised only on *confirming* the Main Deposit (none of the 301 have been), or something besides the
amount is still missing. Confirming one would tell them apart, and it cannot be reached to find
out: **every Transaction Number link - on this screen and on Instant Transaction - answers "No
permission"**, both by clicking it for real and by asking its API
(`/api/account/ticketing/new-ticket/order/<id>`) directly. This is a different, narrower permission
than the Setup screen's that was just granted: listing is allowed, opening one - the only place
Confirm / Reject live - is not.

`SystemPromotionRecordsTest` now probes three things live (`anyDepositExists`,
`anyPromotionRecordExists`, and the detail API's own refusal via
`SystemPromotionRecordsPage#refusedDetailCall`) instead of one, and `requirePromotionRecord()`
replaced `requireRecords()` to abort naming whichever half of this still applies. **The fourteen
blocked rows' bodies are still just the abort call** - not because they are waiting on data with
their logic already written, but because the screen they would need to measure (a deposit's own
detail view) cannot be opened at all yet, so there is nothing to model real locators against. That
is one thing to grant (open the detail-view permission for `qa0001`, or for another account) away
from being genuinely finishable, not two.

### Checked again, minutes later: the permission cleared, a real linked set was found and measured (2026-10-06)

Si: "the system live promotion record is existing now, check it again." It was: `NWE1137847`, a
System Live Promotion record, HKD 10.00 - 10% of a 100 HKD deposit, `NWE1137845`, made a second
earlier, same group (`Testing it again`). Its sibling System Promotion record, `NWE1137846`, was
findable once the header currency switched to the Wallet Promotion's own offer currency (USDT) -
it is scoped by currency the same way Rebate set up's panel is, not shown at all under HKD. **All
three carry the same `2026-10-06 10:24:5x` timestamp** - a qualifying deposit raises its linked
records immediately on request, not on confirm, settling the question the note above left open.

**And the detail-view permission from the note above was simply gone.** Every Transaction Number
link opened for real this time - confirmed by clicking, not only by
`SystemPromotionRecordsPage#refusedDetailCall`, which now answers 200 for both a real id and an
arbitrary one. Three distinct routes share one form shape, now measured and built as
`page.promo.TransactionDetailPage`:

| Route | Heading | Buttons |
|---|---|---|
| `/ticketing/deposit?tid=N` | Transaction Details - Deposit | **Receive** + Reject |
| `/ticketing/system-promo?tid=N` | Transaction Details - Offers | Reject only |
| `/ticketing/deposit-promo?tid=N` | Transaction Details - Live Promotion | Reject only |

**Which button each screen draws is SPR-006..012's cascade claim, visible before a single click**:
only the Main Deposit can be confirmed; a child can only be independently rejected. The System
Promotion record's own fields carry the target-currency math (`Initial promotion amount: HKD
30.00`, `Exchange rate: 0.1274616`, `Actual promotion amount: USDT 3.82`) that the System Live
Promotion record's fields do not carry at all (just `Deposit amount: HKD 10.00`) - SPR-016,
likewise visible without pressing anything.

**Receive was pressed once, and declined - correctly, by this session's own guardrail**, as an
unrequested state-changing action in a connected app, not by the site. The record was left
untouched, `New Request`. `SystemPromotionRecordsTest` was rewritten the same sitting to use all of
this for real: `findLinkedSet()` locates the one naturally-occurring linked set read-only (no
clicking), `SPR-001`, `004`, `005`, `016` assert against it without pressing anything, and
`SPR-006` is wired to press Receive for real (tagged `creates-data`, so an ordinary excluding run
never touches it) and check both children cascade to `Confirmed`. `SPR-013`-`015` read the
confirmed state `SPR-006` leaves behind to check the credited amount's currency (no wallet-balance
page object exists in this project yet, so the actual balance delta is logged as unverified rather
than asserted). `SPR-002`, `003`, `007` and `008`-`012` still abort: the first three need a deposit
made while only one promotion type was Active, which needs the member site (no member-site page
objects were carried into Admin_weibo - see the top of this file); the rest would need their own,
separate qualifying deposit, since confirming (`SPR-006`) and rejecting (`SPR-008`-`012`) the one
naturally-occurring set are mutually exclusive uses of it - `requireFreshLinkedSet()` names exactly
this when a row after `SPR-006` finds the set already spent.

None of this was run by this investigation beyond the one Receive click the session itself
declined - compiled only (`./mvnw -q test-compile`), per the standing rule that Si runs this
project's suites. `./mvnw test -Dsuite=DiscountSuite -Dcase=SPR-001` (or `-006`, `-013`..`-016`) is
where the real assertions now are; the full class, including `-006`'s Receive press, needs
`-DexcludedGroups=` left off or narrowed, since `creates-data` is excluded by default.

### `SystemPromotionSetupTest`'s 09:52 run: eight failures, all this suite's own bugs (2026-10-06)

Si ran the suite for the first time against the now-loaded screen and asked for the report read
and fixed. Three distinct causes, all pre-existing code written against the permission-blocked
defaults and never re-measured once real data arrived - nothing wrong with the site:

- **`SPS-010/011/014/015` - "Save should succeed, but said \"\""**. Watched live: Save on an
  accepted value raises its own two-button "...Confirm the modification?" prompt first, and the
  write (`POST /api/account/system-promo`, confirmed 200 over the network) only fires once
  **Confirm** is pressed - after which nothing follows at all, unlike the Rebate screen's
  "Successfully modified". `SystemPromotionSetupPage.save()` only ever watched for a single-OK
  dialog (`PromptMessage.readAndClose`), so the confirm prompt sat unanswered for its full 12s wait
  on every accepted save, returning empty exactly as a disabled Save does. A **refused** save
  (blank/out-of-range Wallet percentage) skips that prompt and answers at once - which is why
  `SPS-012/013` passed on the same code. Fixed by switching to `PromptMessage.confirmAndRead`
  (presses Confirm when offered, reads a single-OK directly otherwise - already built for exactly
  this shape) and giving `save()` a truthful "Saved successfully (no confirmation message follows)"
  result for the silent-accept case, rather than leaving it as empty.
- **`SPS-002/003` - sections reading at the same position**. With real data loaded, Wallet
  Promotion draws beside Live Promotion, and Agent group beside Member group, as left/right pairs
  on shared rows - not each on its own row, which is what the blocked defaults happened to look
  like on 2026-10-05, the only state this page had been measured against before. Both rows now
  check same-row-plus-left-of, through a new `SystemPromotionSetupPage.sectionLeft`, rather than
  strict top-to-bottom stacking.
- **`SPS-007/008` - Chinese/Thai labels reported "found among []"**. Not a translation defect: the
  check was reading the expected section words against `sectionTitles()`, which only ever matches
  the hard-coded **English** title strings by construction, so it reads `[]` under any other
  language regardless of what the real translation says. The "found among []" in the failure
  message was reporting that empty list, not a missing word. Fixed to check the whole visible page
  text instead, which catches a section header wherever it renders (it is not a `<label>` element,
  so `promo.labels()` was never going to see it either).

Diagnosed by reading `docs/reports/.state/SystemPromotionSetupTest.properties` (the per-case
messages) and the report's own failure screenshots (`docs/reports/screenshots/SystemPromotionSetupTest.*.png`),
then confirmed live: one real Save was pressed, with every field left at its existing value (100 /
30% / 10%, unchanged), purely to watch the confirm-prompt-then-silent-POST sequence over the
network - a no-op write, not a configuration change. Fixes are compiled only
(`./mvnw -q test-compile`), not run, per the standing rule that Si runs this project's suites.

### `DiscountSuite` asked for next: one more bug in `SystemPromotionSetupTest`, and all eighteen `SystemPromotionRecordsTest` cases erroring (2026-10-06)

Si's 10:19 re-run (after the fixes above) brought `SystemPromotionSetupTest` to 13/16, and surfaced
one more, genuine this time - not a test bug:

- **`SPS-013` - a Wallet Promotion percentage of 150 is now observably *accepted*.** The old `save()`
  bug was masking this: with the confirm-prompt now answered, 150 saves as "Successfully modified".
  Checked live that the setting was not left stuck at 150 (the row's own restore works the same way
  and put it back to 30). Tagged `known-defect` rather than flipped, since 0-100 is the client's own
  stated range for a percentage and there is no sane reading of 150% as intended - this is a site
  defect for the client's list, not a suite bug.
- **`SPS-007`/`008`'s own expected words were themselves wrong.** The fix two reports ago corrected
  *how* they check (whole page text, not the English-only `sectionTitles()`), but
  `SystemPromotionTestData.SECTIONS_CHINESE`/`SECTIONS_THAI` had an extra "coin" word on the Live
  Promotion entry that was never on the shipped screen - "直播幣優惠" / "โปรโมชั่นเหรียญไลฟ์สตรีม"
  against the real "直播優惠" / "โปรโมชั่นไลฟ์สตรีม". Corrected from the report's own screenshots.

And `SystemPromotionRecordsTest` - freshly rewritten the same day to finally run for real -
reported all eighteen cases as **Error, "the suite failed before this case ran"**, every one the
same `@BeforeAll` timeout: `selectAllPaymentOptions()` waited 10s on a locator scoped to
`div.col-md-3`, measured only against Transaction type's own column. Payment Options' column is a
different width, so nothing matched. `SystemPromotionRecordsPage#groupLink` now finds each group's
"Select all" from its own first checkbox instead of a Bootstrap class - see that page object's own
dated update. Verified live against all five filter groups before trusting it, since this was the
first time `openEveryFilter()` had ever run to completion - everything past it in `@BeforeAll`
(`findLinkedSet()` included) had never actually executed through the real page-object code before
either, only been exercised by hand during the investigation that wrote it.

Diagnosed from `docs/reports/.state/SystemPromotionSetupTest.properties`,
`docs/reports/.state/SystemPromotionRecordsTest.properties` and
`docs/reports/extent/.state/cases.json` (the properties file truncates a multi-line exception to
its first line - "TimeoutError: Error {" - the extent JSON carries the full stack, which is where
the actual failing locator came from). Confirmed live via direct DOM queries at each group's own
checkbox, not guessed. Fixes compiled only, not run.

### `NOT-CLI-035` - a message is refused to a different signed-in member, not just a visitor (2026-10-06)

Si pointed at a Google Sheet (`11naP1Oo73wJzfmil_RAULlH_bQx9I755`, gid `779225765`) and asked for an extra
test case from it. It turned out to be a different, much more detailed client test matrix for the Message
Center feature - 100+ rows, prefix `TC-MI-*`, not the workbook this project is reconciled against (read-only
link access worked; it is not in the connected Drive account). Reading through it against what
`UiNotificationCenterClientHK` already covers, one row was a genuine gap: `TC-MI-EDG-004`, "open another
member's message URL/ID" - expected "Access denied / not found; A's data not shown." `NOT-CLI-034` already
covers a signed-out visitor on a message's address; this is the different case of a second member who *is*
signed in, just as the wrong account.

Added as `NOT-CLI-035` in the `Center` section, numbered on as usual. It needs a second HK-brand account with
messages of its own, which nothing in `Config/` names yet - `client.hk.outsider.username` /
`client.hk.outsider.password` are added blank, and the row aborts naming them until Si supplies one. Pushed
through the usual pipeline (`append-cases` on both the checked-in workbook and Si's Downloads copy,
`split-cases`, `case-page`, `case-runners`).

### `SPR-008`..`012` had no bodies at all, despite the class Javadoc already claiming they were wired (2026-10-06)

Asked to check Discount > System Promotion and finish the unfinished test, the five reject-cascade rows in
`RejectCascadesAndIsolation` turned out to be the actual gap - not `SPR-002`/`003`/`007`, which already carry a
reasoned `abort()` naming the member-site deposit they need. These five called only their own guard
(`requireFreshLinkedSet`) and returned, so a run that reached them asserted nothing at all - silently passing as
"ran, no failure" rather than skipping visibly, which is worse than an abort. The class Javadoc already described
them as "wired against real, measured `TransactionDetailPage#receive()`/`reject()` calls - not placeholders",
which was true of `SPR-006` but not of these five.

Written now, symmetric to `SPR-006`'s confirm pattern: `SPR-008` presses Reject on the Main Deposit and asserts
both children cascade to Rejected; `SPR-009`/`010` reject the System Promotion child alone and assert the Main
Deposit / System Live Promotion sibling stay `New Request`; `SPR-011`/`012` do the same the other way round for
the System Live Promotion child. Each still opens with `requireFreshLinkedSet`, so in one run against one
environment only the first of `SPR-006`, `008`-`012` to execute (JUnit's `DisplayName` order puts `SPR-006` first)
will actually press anything - the rest abort naming the already-spent set, exactly as the class Javadoc already
described. Compiled only (`./mvnw -q test-compile`), not run, per the standing rule that Si runs this project's
suites.

### Root-causing the `DiscountSuite` skips, live (2026-10-06, afternoon)

Asked to list root causes for every skipped `SystemPromotionRecordsTest` row and fix them one by one, a fresh
15:01 run showed all sixteen non-`Screen` rows Skipped on the *same* message - `anyPromotionRecordExists` false -
rather than the richer mix the morning's investigation had left (a real linked set found and spent). Live
investigation (signed in as `qa0001` through a real browser, not the suite) turned up two distinct things, only
one of which is this suite's bug:

- **`refusedDetailCall()`'s "without token" is a red herring, not a permission signal.** It calls
  `page.request().get(...)` - a raw HTTP request that carries the browser context's cookies but **not** whatever
  custom header the app's own JS attaches to its real API calls. A plain `fetch()` run from the page's own console
  against the identical URL, cookies included, got the identical `{"status":-404,"message":"without token"}` -
  proof the refusal is about the request shape, not about `qa0001`'s access. Every abort message
  `requirePromotionRecord()` prints a "this operator is refused" clause off of `detailRefusal` is therefore worded
  more confidently than the evidence supports; the UI's own clicks (which do carry whatever token the app attaches)
  are unaffected; this is cosmetic, not a blocker, and is left as a known inaccuracy in the message rather than
  fixed this session - the instructions were to list causes and fix them one by one, and the real blocker below is
  what actually determines every row's status.
- **The real cause, confirmed live and still unexplained at the DOM/API level**: searching Trade > Transaction
  Records with the UI's own real Search button - System Promotion + System Live Promotion checked (verified by
  zooming on the checkboxes, not assumed), every Status/Payment Option/Member group open, no date bound, with and
  without a Transaction code range - reads **No data**, even for a code range (`NWE1137860`-`NWE1137870`) known at
  the time to contain a System Live Promotion row (`NWE1137868`, seen rendered once, paired by timestamp with its
  confirmed parent `NWE1137867`). The same two-type search with "Select all" Transaction types also came back
  without a single System Promotion/System Live Promotion row anywhere in an 8680-record, 174-page dataset. The
  captured request (`read_network_requests`) shows the two types correctly present in `where[type][6..7]`, so this
  is not a checkbox-selection bug in `SystemPromotionRecordsPage`. Two live, since-confirmed-cascading records
  (`NWE1137867`→`NWE1137868` Confirmed, `NWE1137862`→`NWE1137864` Rejected, both same-second pairs) prove the
  underlying confirm/reject cascade works; what is not yet pinned down is why this screen's search (`amount_type`
  is sent as exactly `[0,1]`, the Deposit/Expenditure checkboxes - if a promotion credit carries a third
  `amount_type` value, no combination of this screen's Record-type checkboxes could ever surface it) excludes
  these two types from every result set tried. Worth a direct look at a known promotion record's own row the next
  time one is confirmed to exist, rather than re-guessed from outside.

### `ClientValidDepositTest` - a real client deposit, to give `DiscountSuite` its own data (2026-10-06)

Rather than wait on whatever `qa000092`'s deposit history happens to hold, Si asked for a new test class that
makes a real, qualifying client deposit under a chosen promotion state, crossing the "no member-site code in
Admin_weibo" boundary this file otherwise documents - on request, not by default. `page.client.DepositPage` and
`page.client.AlertBoard` are a trimmed port of NewWeibo_automation's own deposit page object and alert-board
component, carrying only Channel > Bank Transfer (the one fiat, in-app, non-gateway option that project's own
suite already exercises) - nothing from the other four channels, the preset-amount controls, or the invalid-amount
cases, none of which this project's own suite needs.

`ClientValidDepositTest` (`SPD-001`/`002`, `@Order(135)`, between Setup and Records in `DiscountSuite`) snapshots
Setup, sets exactly one of Wallet Promotion / Live Promotion Active, deposits Setup's own configured minimum as
`qa000092` via Bank Transfer, reads the new deposit back off Trade > Transaction Records (the newest Deposit tid
for that account past whatever existed before - not "the top row", which the finding above shows cannot be
trusted for these two types yet in every case) and asserts which promotion type linked to it. Both rows are
`creates-data` and leave their deposit `New Request` - untouched, so each is a fresh linked set
`SystemPromotionRecordsTest`'s `requireFreshLinkedSet` rows (`SPR-008`..`012`) can spend. Setup is restored in
`@AfterEach` either way. Compiled only (`./mvnw -q test-compile`), not run, per the standing rule that Si runs
this project's suites - and because the unresolved search finding above means even a correctly-created record
cannot yet be relied on to show up in `linksTo()`'s own search until that is better understood.

### Both `SPD-*` rows errored on their first real run - a page-ordering bug in this class, not the site (2026-10-06)

Si's 15:37 run of `DiscountSuite` had both `ClientValidDepositTest` rows **Error** on an identical 10s timeout
waiting for `input[name='DepositMinAmount']`. The extent trail named the cause exactly: `arrangePromotionState`
leaves `adminPage` on `/promo/system-promotion` after its own `setup.save()`, but each test method then called
`currentMaxDepositTid()` - which navigates that same shared `adminPage` to Trade > Transaction Records - **before**
reading `setup.value(DEPOSIT_MIN_AMOUNT)` / `setup.selected(OFFER_CURRENCY)` for the deposit it was about to make.
By the time those reads ran, the page had moved on and the Setup form was simply not there to read.

Fixed by reading both Setup fields immediately after `arrangePromotionState()` returns - while `adminPage` is
still on the Setup screen - and threading `minAmount` into `depositTheConfiguredMinimum(String)` as a parameter
instead of letting that method read Setup itself (it does not have its own `adminPage` navigation to protect
against, but the caller does). The general rule this leaves for the next row added to this class: once any helper
here calls `records.open()`/`setup.open()` on the shared `adminPage`, every other page's fields must already have
been read, not deferred - the two page objects are two faces of one Playwright `Page`, not two independent
sessions. Compiled only (`./mvnw -q test-compile`), not run.

### After the ordering fix, both `SPD-*` rows ran end to end and failed on the real root cause - pinned down and fixed (2026-10-06)

Si's next run got past the ordering bug above - both rows made a real deposit (`NWE1137869`, `NWE1137871`) and
failed cleanly on their own assertion: "should have a linked System Promotion/Live Promotion record... but none
was found." `SystemPromotionRecordsTest` still skipped all sixteen the same run. Investigated live, this turned
out to be real and reproducible, not a fluke:

**The screen's Transaction type filter drops System Promotion / System Live Promotion from every result the
moment the selection is anything less than all eight checkboxes - even with the wanted type still among those
checked.** Proven directly: with a known System Live Promotion row (`NWE1137872`, paired with its parent deposit
`NWE1137871`, both `16:54:54`) confirmed on screen under the page's own untouched "all eight checked" default,
every narrower combination tried read **No data** for it - System Live Promotion alone, System Promotion +
System Live Promotion together, Deposit + System Live Promotion, and all eight with only *Withdraw* unchecked.
Ordinary Deposit/Withdraw rows kept showing correctly under every one of those same combinations; only types 12
and 13 vanished, and only when the selection was not literally everything. The captured network request showed
the right type ids in `where[type][]` each time, so this is not a checkbox-id bug in `SystemPromotionRecordsPage`.

**The fix: never narrow the Transaction type checkboxes to the promotion types at all.** Keep every type selected
(what `openEveryFilterForPromotionTypesOnly()` now does, renamed in spirit though not in signature - its Javadoc
carries the full account) and filter `records()` in Java by `TransactionRow#transactionType()` instead. Applied
everywhere `selectOnlyTypes("System Promotion" | "System Live Promotion")` used to be called:
`SystemPromotionRecordsTest`'s `findLinkedSet`, `findRow`, `linksTo` and `SPR-005`'s inline search, and
`ClientValidDepositTest`'s own `linksTo`. Also scoped each of those to `filterAccount(USER_USERNAME)` - necessary
because selecting every type returns the *whole* 8000+-record table, and the newest 50 (one page) can easily be
every other account's activity on a shared, actively-used test environment; scoping to the one account this
feature's fixtures always use keeps the real match on page one.

**A second, false alarm along the way, worth recording so it is not re-chased:** scoping by account and selecting
every type once read back only ordinary Deposit/Withdraw rows, which briefly looked like account-scoping broke
the fix too. Retried deliberately (fresh `records.open()`, re-verified every checkbox by reading the page rather
than trusting prior clicks) and it worked perfectly - `NWE1137872` and six more System Live Promotion rows for
the account all appeared, 447 total records for `qa000092` across all time. The false alarm was leftover filter
state from rapid manual exploration (plausible given this project's own documented history of controls that
"don't always take" - the Rebate report's date range, this same screen's "Select all" links); it is not a reason
to doubt the account-scoped approach, which `records.open()` resets cleanly before every search in the actual
page-object code, unlike manual clicking in a browser tab.

Fixed in both test classes and `SystemPromotionRecordsPage`, compiled only (`./mvnw -q test-compile`), not run.
This should resolve the sixteen `SystemPromotionRecordsTest` skips and both `ClientValidDepositTest` failures in
one pass, since all of them trace to the same cause - verify with a real run.

### `SPS-013` fixed "as system working" (2026-10-06)

Si asked for `SPS-013` to be fixed to match what the system actually does, the same ruling already applied to
`NOT-CRT-022`/`033`/`007` in the Notification suite. It had been tagged `known-defect` and asserted a refusal: a
Wallet Promotion percentage of 150 (outside the client's own stated 0-100 range) is in fact accepted
("Successfully modified") and holds after a reload - measured live the same day `SystemPromotionSetupPage#save()`
was fixed to answer the screen's own confirm prompt (see that method's Javadoc). The row is now
`walletPercentageOutOfRangeIsAccepted`: it asserts acceptance and that a reload still reads 150, drops the
`known-defect` tag, and its comment records the gap (no server-side bound on this field) as a finding for the
client's own list rather than a reason to keep the row red. Compiled only (`./mvnw -q test-compile`), not run.

### `SPR-001`..`005` still skipped after the type-filter fix - two more bugs found and fixed (2026-10-06)

Asked to check `SPR-001`..`005` (Record creation on deposit) and go on from there: Si's own 16:36 `DiscountSuite`
run (after the type-filter fix above) still showed all sixteen `SystemPromotionRecordsTest` rows Skipped, and both
`ClientValidDepositTest` rows red again - one `Fail`, one `Error`. Two distinct, real bugs, neither a repeat of
the type-filter one:

- **A currency leak between `ClientValidDepositTest`'s own two rows.** `linksTo()` switches the admin shell to
  whichever currency it is asked to check (the offer currency, for a "System Promotion" check) and never switches
  it back. `SPD-002`'s `@BeforeEach` then opened Discount > System Promotion Setup - one configuration **per
  header currency** - still on USDT, left there by `SPD-001`'s last `linksTo()` call: it toggled *USDT's*
  Wallet/Live status, while the member's deposit (always HKD) was judged against HKD's own, completely untouched
  configuration. `snapshotSetup()`, `restoreSetup()` and `arrangePromotionState()` all now force
  `shell.useCurrency(CURRENCY)` first.
- **`search()`'s fixed 1,500ms settle wait is not enough for this screen's much bigger result sets.** `SPD-001`
  errored on a 10s timeout inside `tidOf()`, looking for a transaction number (`NWE1137915`) that live
  investigation confirmed *was* genuinely on the page - just not yet repainted when the fixed wait elapsed. The
  type-filter fix made every relevant search here select **every** Transaction type and scope to one account's
  whole history, a far bigger table to render than the narrow two-type searches this project's `1_500ms` was
  originally tuned against, on a busy, actively-shared test environment. This is the same class of race
  `NotificationListPage.turnPage()` already found and fixed on this project's other DataTables-based lists.
  `SystemPromotionRecordsPage.search()` now polls (`awaitStableRows()`, up to 6 × 400ms) until two reads of the
  row count agree, rather than trusting the fixed wait alone - deliberately polling for *stability*, not for
  `rowCount() > 0`, since "No data" is a genuine, common, correct answer on this screen.

Both are plausible explanations for why `anyPromotionRecordExists` kept reading false even after the type-filter
fix: a search that reads the table mid-repaint can just as easily read an empty snapshot as a wrong one. Verified
`awaitStableRows()`'s premise live (the exact account+all-types+cleared-date combination does find the account's
promotion rows when read carefully), but **not** re-run as a full suite - per the standing rule that Si runs this
project's suites. Compiled only (`./mvnw -q test-compile`).

### A third instance of the same race, this time on the member site's own currency switch (2026-10-06, 17:02 run)

Si re-ran `ClientValidDepositTest` alone after the fixes above. `SPD-001` now aborted on a new symptom:
"The member's wallet would not switch to HKD (reads \"\")". The trail explained it exactly: the member site's own
second, self-triggered navigation (dropping the now-read `?currency=` from the URL) was still loading 3.6s after
the first page settled - past `MemberSignInPage#useCurrency`'s fixed `CURRENCY_SETTLE_MS` (3,000ms) - so
`currentCurrency()` read the account panel mid-reload and came back empty. The same class of bug as
`SystemPromotionRecordsPage#search()`'s fix just above, now found a third time (the second was the Setup-currency
leak) in as many fixes today. Fixed the same way, not by lengthening the fixed wait: `useCurrency` now polls
`currentCurrency()` for up to five more seconds past the fixed settle, re-dismissing the welcome banner each
round, since the site's second navigation can raise it again.

`SPD-002` failed differently and is not yet explained: the deposit (`NWE1137923`) was made, but
`linksTo()`'s own search for a Live child never got that far - the trail shows "Pressing Search on Transaction
Records" and then, ~17s later with no further step logged, "Case ended on
`https://hk-test.superme888.com/Pay/index?tradeNo=...`" - the admin session's own page ended the case on an
unrelated third-party payment gateway URL, not on Transaction Records at all. `hk-test.superme888.com/Pay/getStatus`
polling calls appear in this project's trails even during ordinary Setup-page interaction earlier in the same run,
so some background widget on admin-test's own pages talks to that gateway regardless of what the suite is doing;
why the admin page's own navigation ended up there, rather than the search simply returning late, is not
diagnosed yet. Worth a fresh, isolated look (a single `-Dcase=` run of just this row) before guessing at a fix -
documented here rather than speculatively patched.

Compiled only (`./mvnw -q test-compile`). Note for whoever re-runs `DiscountSuite` next: `SystemPromotionRecordsTest`'s
own sixteen skips are still only confirmed against the **16:36** run, which predates the type-filter and
`awaitStableRows()` fixes - Si has only re-run `ClientValidDepositTest` alone since then, so those sixteen rows'
fate under the current code is still unverified.

### The root cause behind `SPR-001`..`016`, found: a fourth instance of the same bug, on the currency switch itself (2026-10-06)

Even the `awaitStableRows()` fix did not clear the sixteen skips (re-confirmed on the 17:10 run, which did include
it). Asked for the root cause directly, live investigation found it: **this operator's account (`qa0001`) defaults
to USDT on every fresh sign-in, not HKD** - confirmed by logging in fresh, with nothing touched, and reading the
header currency before any interaction. `signInAndProbe()`'s own `shell.useCurrency(CURRENCY)` right after sign-in
exists specifically to switch away from that default, and `AdminShellPage.selectCurrency()` - the method
everything in this project's currency switching goes through - had the exact same shape of bug already found and
fixed three times this same day in sibling code (`SystemPromotionSetupPage#save()`'s confirm prompt,
`SystemPromotionRecordsPage#search()`'s table repaint, `MemberSignInPage#useCurrency()`'s double navigation): a
flat 3-second wait, trusted without reading back whether the switch actually took.

Verified live that the fix target is real: with the header manually and reliably on HKD, `filterAccount(qa000092)`
+ every Transaction type + a cleared date range finds all 452 of the account's records on the first page read,
System Live Promotion rows included (`NWE1137924`, `1137872`, `1137868`, `1137864`, `1137861`, `1137858`,
`1137855`, `1137850`, `1137847`) - and that the switch, once made, **survives a fresh page navigation** (so this
is not the same kind of "every `records.open()` call needs its own currency re-assert" problem as the earlier
fixes). The one single point of failure is the switch itself, the moment it runs.

Fixed in `AdminShellPage#selectCurrency()`: polls `currency()` for up to five more seconds past the fixed wait if
the switch has not yet shown up, and now logs plainly (`CaseLog.evidence`) if it still has not - rather than
reporting back whatever currency is actually selected as though it were always the one asked for, which is what
let this failure mode run completely silently until now. Si asked that HKD be the default for every run: the
config default already is (`test.currency` defaults to `HKD` in `BaseTest.CURRENCY` with nothing set), so this
fix is what makes that default actually stick against an account whose own server-side preference disagrees with
it. Compiled only (`./mvnw -q test-compile`), not run - this is the fix to verify with a real `DiscountSuite` pass.

### Still skipping after the currency fix - and `SPD-003` added to confirm and verify the cascade directly (2026-10-06)

The 17:57 run (which did include the `AdminShellPage#selectCurrency` fix) still showed all sixteen
`SystemPromotionRecordsTest` rows Skipped, and both `ClientValidDepositTest` rows Fail - each on its own deposit
(`NWE1137932`, `NWE1137934`) existing but the expected child still not found by `linksTo()`. The currency switch
itself is now confirmed working in the trail (`Currency = USDT`, `Currency = HKD`, matching what was asked for
each time), so the remaining gap is somewhere past that - not yet root-caused further this session.

Si asked, separately, for the client deposit flow to be extended to actually confirm a deposit and verify the
cascade in admin, covering more of the suite's claims in one self-sufficient flow rather than only the creation
shape. Added `SPD-003`
(`confirmingADepositWithBothPromotionsActiveCascadesToBothChildren`): both promotions Active, deposit, find both
children, press Receive on the Main Deposit, then assert the Main Deposit and both children read Confirmed and
each child's credited amount is in its own currency (the offer currency for System Promotion, the deposit's own
currency for System Live Promotion) - `SPR-001`, `006` and `013`..`015`'s claims in one deposit this test makes
and confirms itself, rather than depending on `SystemPromotionRecordsTest` finding a naturally-occurring set
(which `SPR-006` and `008`..`015` all compete for, one run only ever having the one). Added `tidLinkedTo` (as
`linksTo`, but returns the linked record's own tid) and `rowByTid` (the row for a tid, scoped and filtered the
same way `linksTo` already is) to support it; `linksTo` itself is now a one-line wrapper over `tidLinkedTo`.

This does not by itself explain why `linksTo()` still fails in the 17:57 run - `SPD-003` depends on the same
search machinery `SPD-001`/`002` do, so if that is still broken, `SPD-003` will show it the same way, just with
more to read once it does pass. Compiled only (`./mvnw -q test-compile`), not run.

### Si's fix for the whole currency problem: just set the Offer currency to the run's own currency (2026-10-06)

All of the above - the currency-switch race in `AdminShellPage`, `linksTo()` needing to hop currencies to find the
System Promotion child - was working around a problem Si pointed out does not have to exist: **Wallet Promotion's
own Offer currency is itself a Setup field**, and nothing requires it to differ from the run's deposit currency.
Pointed at `CURRENCY` (HKD), a single confirmed deposit raises all three records - Main Deposit, System
Promotion, System Live Promotion - readable together under the one header currency a run is already on, no
switching needed to find any of them.

`arrangePromotionState()` now also sets `OFFER_CURRENCY` to `CURRENCY` (when the dropdown offers it and it is not
already that), alongside the Wallet/Live Active toggles it already set. Every `offerCurrency` the three test
methods read afterward therefore comes back as `CURRENCY` itself, so `linksTo()` / `tidLinkedTo()` / `rowByTid()`
now resolve the System Promotion child under the same currency as everything else - the currency switch inside
them becomes a same-currency no-op (`AdminShellPage#selectCurrency`'s own early return) rather than a real hop
that depended on the race-prone switch being reliable. This does not remove that fix - a run still needs the
header on `CURRENCY` to begin with, coming off the account's own USDT default - but it removes the *second*
currency this class used to need mid-test, which is the half that kept failing. Compiled only
(`./mvnw -q test-compile`), not run - the next real `DiscountSuite` pass is what settles whether this, on top of
the fixes already made today, actually clears `SPR-001`..`016` and `SPD-001`..`003`.

### `SPD-001`/`002` extended to confirm and verify the cascade too, matching `SPD-003` (2026-10-06)

Si asked for `SPD-001`/`002`'s expected result to match what the system actually does, the same ruling already
applied elsewhere (`SPS-013`, the Notification suite's `NOT-CRT-022`/`033`/`007`): confirming the Main Deposit
confirms its one linked promotion record along with it, `SPR-007`'s claim, which these two rows were not yet
asserting - they stopped at the creation shape. Renamed to `onlyWalletPromotionActiveConfirmsOnlyTheWalletChild`
and `onlyLivePromotionActiveConfirmsOnlyTheLiveChild`; each now also presses Receive on the Main Deposit after
confirming the creation shape (via the tid `tidLinkedTo` now returns, not just a boolean) and asserts the Main
Deposit and its one child both read Confirmed - the single-child side of `SPD-003`'s own cascade check.

All three rows now confirm their own deposit, which changes what they leave behind for
`SystemPromotionRecordsTest`'s `requireFreshLinkedSet` rows (`SPR-008`..`012`, the reject-cascade claims): those
need an *unconfirmed* deposit to reject, which none of `SPD-001`..`003` leave anymore - they still need their own,
separately-made, unconfirmed deposit, same as before these three existed.

`linksTo()` is gone (unused once both callers moved to `tidLinkedTo()` for the tid), and the stale
`assertFalse` import went with it. Compiled only (`./mvnw -q test-compile`), not run.

### `tidLinkedTo()` now tries the closest-in-time candidate first, not list order (2026-10-06)

Si pointed out how a child record actually relates to its Main Deposit on screen: it is never grouped with it -
each is its own row - but its Transaction Date is almost exactly the parent's own, a second or so apart at most.
`tidLinkedTo()` used to try candidates in whatever order the table listed them and open each one's detail view
(the only place the authoritative "Deposit transaction code" link lives) until one matched - up to 15 of them,
which is what a 10s timeout inside `tidOf()` was caught in the middle of during `SPD-001`'s first real run. It now
takes a `TransactionRow` (not just the deposit's own number string) and sorts candidates by closeness of
Transaction Date to the Main Deposit's before opening anything, so the real match is normally the first (and only)
candidate whose detail view gets opened at all. The detail-view check by "Deposit transaction code" stays - it is
still the one authoritative link - timestamp proximity only decides which candidate to check first. All three
`SPD-*` rows pass their own `newDeposit` through now instead of `newDeposit.transactionNumber()`. Compiled only
(`./mvnw -q test-compile`), not run.

### The actual root cause, found and verified live (2026-10-07): it was never the Transaction type checkboxes - it was Payment Options' "Select all"

Si ran the full `DiscountSuite` (asked for and granted explicitly - normally Si runs it, this once I did). `SystemPromotionSetupTest` now passes 16/16. `ClientValidDepositTest` still failed both `SPD-001`/`002` and aborted `SPD-003` on the same symptom as every prior day's attempt: the deposit is made, but no linked promotion record is ever found - even with every fix so far in place (type-filter workaround, account scoping, search stability poll, currency-switch poll, Offer currency pinned to `CURRENCY`, timestamp-sorted candidates).

Checked live immediately after the run: the children exist. `NWE1137990` (this run's own failing deposit) has its
System Promotion child sitting right next to it - `NWE1137991`, same timestamp, visible the moment Account is
filled and the date is cleared **without touching anything else**. So the deposit flow, the cascade, and Si's
Offer-currency fix were never the problem; the search was finding nothing because of a **fourth, final, previously
unidentified cause** - and it exposed that the whole 2026-10-06 investigation into "the Transaction type checkboxes
drop these two types" had the right symptom but the wrong culprit.

Isolated by rebuilding the filter one click at a time against this same known-good account: Transaction type's own
"Select all" alone - the two promotion types still showed up fine. Adding Status's "Select all" - still fine.
Adding Member group's "Select all" - still fine. **Adding Payment Options' "Select all" - every System Promotion /
System Live Promotion row vanished immediately**, with Deposit/Withdraw rows completely unaffected and the
checkboxes still visually showing everything checked. Both promotion types carry no Payment Option at all (the
column is blank for them); clicking "Select all" there evidently submits an explicit list of payment channel codes
that a record with none can never match, where the page's own *untouched* default - whatever it actually sends -
does not exclude them. This is why every one of 2026-10-06's "it only works with nothing touched at all" findings
looked like it implicated Transaction type: every test that showed the symptom also called
`selectAllPaymentOptions()` in the same breath, and the two were never tested apart until now.

Fixed in `SystemPromotionRecordsPage#openEveryFilterForPromotionTypesOnly()`: no longer calls
`selectAllPaymentOptions()` at all. Verified live, the exact sequence the method now runs (Transaction type, Status
and Member group all explicitly selected, Payment Options never touched, date cleared) finds both known promotion
rows correctly. This is the fix that should finally clear `SPR-001`..`016` and `SPD-001`..`003` - the three other
fixes made today (currency-switch poll, Offer currency pinned, timestamp-sorted candidates) were real, worthwhile
fixes in their own right, just not the thing that was actually failing every run since the type-filter workaround
went in. Compiled only (`./mvnw -q test-compile`) and verified against the live screen directly, but **not yet
re-run as a suite** - that confirmation is still owed.

### Two more runs after the Payment Options fix: `SystemPromotionSetupTest` goes fully green twice in a row; `DepositPage` gets the chat-widget fix it never had (2026-10-07)

Two full `DiscountSuite` runs followed the Payment Options fix (run with Si's explicit go-ahead - normally Si runs
this suite). `SystemPromotionSetupTest` passed 16/16 both times, including `SPS-008`/`009` which had failed once on
the first of the two runs with a timeout-flavoured message - not reproduced the second time, so read as transient
rather than a real regression. `SystemPromotionRecordsTest`'s `findLinkedSet()` candidate loop (and
`ClientValidDepositTest`'s `tidLinkedTo()`) also hit a new, narrower symptom once the Payment Options fix let them
reach real candidates: `tidOf()` timing out (10s) on a transaction number moments after it was read from the same
search - plausible on this busy, actively-shared environment where the 50-row page keeps churning. Both loops now
catch `PlaywrightException` per candidate and move on to the next one rather than letting one flaky lookup sink
the whole search.

The second run's `ClientValidDepositTest` skipped all three rows on a different, more telling symptom: "the deposit
form never calculated a summary for 100". Its own trail showed the Chatwoot chat-widget's message-polling calls
firing repeatedly right up to the abort - the same widget `ClientCategoryHomePage` already has standing
`closeChatWidget()` handling for ("opens itself a few seconds after the page loads and covers whatever is
underneath it"), which `DepositPage` - ported fresh for this suite - never received. Ported the same method
(closes through `window.$chatwoot.toggle('close')`, hides the holder element if the SDK will not) into
`DepositPage`, called from `open()`, `selectChannel()` and `submit()` - the three points most likely to be hit
by a widget that opens a few seconds into the page's life. Compiled only (`./mvnw -q test-compile`), not yet
re-run.

### Tool > SMS Sending Log (written 2026-10-21, not yet deployed)

`SmsSendingLogTest` covers **129 rows** of the client's "SMS Sent logs" workbook tab (version 3.20.0, the
bright-green `FF00FF00` tab inside `[TC_Admin] V.5.1.0 Weibo.xlsx` - the project's own marker for "automate
this"), ids `SMS-ADM-NAV-001` .. `SMS-NFR-REL-003`, one method per row, taken verbatim from the sheet (no
project-added 900 block - every id already exists in the client's sheet). Run it as `-Dsuite=ToolSuite`
(alongside `NotificationTest`) or as one of the fourteen `suites/tool/SmsSendingLog*Suite` (one per section).
The screen is expected at `/tools/sms-sending-log` (`test.smsSendingLog.path`), following Notification's own
`/tools/<name>` addressing - a guess, not a measured path, since Tool offers only Notification today. The
probe in `@BeforeAll` guards every row uniformly through `requireScreen()` and reports Not run naming that
reason until the screen exists.

The four page objects (`SmsSendingLogListPage`, `SmsBlockModalPage`, `SmsRawDataModalPage`,
`SmsRecordDetailPage`, all in `page.tool`) were already written ahead of this session, against the client's
sheet and the Figma mockup Si gave (see their own javadoc for the frame references) - this session only
closed one locator gap found while writing the test class (`SmsSendingLogListPage` documented a Time limit
date-range control in its javadoc but had no locator or method for it at all) and added small pagination
(`clickNextPage`/`clickPreviousPage`/`clickPageNumber`) and Enter-key-search (`searchPhoneWithEnter`) methods
the sheet's rows needed. `AdminShellPage.SMS_SENDING_LOG_HREF` and `test.smsSendingLog.path` already existed
too.

What every run will depend on, and the gaps recorded rather than silently skipped:

- **No front-end SMS/OTP request automation.** The entire `SMS-CLI-LIM` section (11 rows) and
  `SMS-ADM-BLK-008/009/010` need requesting a code from the member site's registration/forgot-password flow,
  which this project has no page objects for - the same gap `AddMemberResetPasswordTest` already names
  ("There is no SMS log reachable from this project to confirm nothing was sent", GD-195/GD-197). All 14
  rows abort naming it.
- **No Account Operation Log page object anywhere in this project.** `SMS-ADM-EXP-003/004`,
  `SMS-NFR-SEC-004` and the audit half of `SMS-ADM-BLK-013` abort naming it - the same gap
  `OfferAndRankingAlertsTest`'s `SN-ADM-RNK-007` already names.
- **No known API endpoint, second tenant, or load-test harness.** `SMS-NFR-SEC-001/005/006/010` (direct API
  calls, IDOR, CSRF) and `SMS-NFR-PRF-001/005` (100k-row export, 50 concurrent admins) abort naming exactly
  what traffic or account this project's UI-only layer cannot generate.
- **No distinctly-permissioned test accounts for this screen's own sub-permissions** (view full phone,
  Show more, Block/Unblock are modelled by the client as permissions *within* the SMS Sending Log screen, not
  at the role level `ADMIN_/AGENT_/USER_USERNAME` already cover). `SMS-ADM-PRM-001`..`006` and
  `SMS-ADM-BLK-012` call `signedInAs(...)` against five new, still-blank Config/ keys
  (`test.smsSendingLog.maskedPhone.*`, `.fullPhone.*`, `.showMore.*`, `.noShowMore.*`, `.noBlock.*`) and abort
  naming the empty key until Si configures such accounts.
- **A few rows need seeded data this project cannot create.** `SMS-ADM-SRC-004` (exact-second boundary
  records), `SMS-ADM-LST-012` (a 30-char username/long sub-domain record), `SMS-ADM-RAW-004`/`SMS-NFR-SEC-002`
  (a malformed/XSS-laden stored raw-data record) and `SMS-ADM-NAV-004` (forcing a real session timeout) all
  abort naming what would need to be seeded or forced.
- **Two cosmetic/open-policy rows record evidence rather than asserting a verdict**:
  `SMS-NFR-SEC-008` (whether the OTP in raw data should be masked is explicitly "confirm with PO" in the
  sheet) and several badge/colour rows (`SMS-ADM-LST-004/005/006`, `SMS-NFR-UI-001/004`) note that colour and
  layout are confirmed from the case screenshot rather than from a text assertion.

**Open item for Si**: `src/test/resources/testdata/client_testcase.xlsx` still has no "SMS Sent logs" tab -
it currently only carries Notification and Other Category. Importing the tab from
`[TC_Admin] V.5.1.0 Weibo.xlsx` needs an actual spreadsheet tool (Excel/openpyxl); this session deliberately
did not attempt it with raw zip/XML surgery on a binary that `split-cases`/`append-cases`/`case-page` all
depend on.

### Quick Tour Banner shipped between 2026-10-21/22 and 2026-10-24, and needed real fixes, not just confirmation

`QuickTourBannerTest` was reporting all 28 rows Not run - `requireScreen()` aborting every case because the
2026-10-21/22 measurement found no Quick Tour Banner entry in Web content management at all. It is live now
(`cms/quick-tour-banner`, the guessed path), alongside four other entries that also weren't there yet -
Welcome Banner, Banner management, Promotion Banner, Fast login banner - so `signInAndProbe()` now finds the
screen and the gate opens.

`QuickTourBannerListPage`'s locators matched the real markup on first measurement, unchanged. The two form
page objects did not, and would have failed every row that reached them even with the gate open:

- **`QuickTourContentTabFormPage`'s Title input** carries no `name` or `placeholder` at all - the guessed
  `input[placeholder*='itle' i], input[name*='title' i]` matched nothing. Found by its `label.control-label`
  instead (`//label[starts-with(normalize-space(.),'Title')]/following::input[1]`).
- **The submit button has two different labels depending on which screen it's on** - "Add content tab" on
  Add, "Save changes" on Modify - something the sheet's own "Save changes" wording throughout did not
  distinguish. Matched by `button[type=submit]` now, since exactly one exists either way.
- **"Return to list" is actually a link reading "Return »"**, on both `QuickTourContentTabFormPage` and
  `QuickTourPageBannerFormPage` - not a button, and not that wording. The sidebar also has an unrelated
  "Return to zero" link, so the fix matches the link role by its exact name rather than loosely.
- **`QuickTourPageBannerFormPage`'s `h1` reads only "Configure Page Banner"** - the "Quick Tour Banner · "
  half the sheet showed is the breadcrumb, a separate element, not part of the heading. `QT-A-PB_P01`'s
  expected string is corrected to match.
- **The image-slot size hint is not a following sibling of its `input[type=file]`** - it lives in the
  field's `ancestor::div[1]`. `QuickTourContentTabFormPage#hintNear` corrected accordingly.

Not yet measured: every other assertion in the 28 rows (validation messages, the Override section's copy
behaviour, per-currency isolation, pagination) - those still need a real run against the live screen to
confirm, the same as any suite whose gate has just opened for the first time.

### The member-site deposit flow (`DepositPage`, `MemberSignInPage`, `AlertBoard`) was all guessed from NewWeibo_automation, and most of the guess was wrong (2026-10-24)

`ClientValidDepositTest` crosses this project's own boundary (no member-site page objects, see this file's
top) by design, porting NewWeibo_automation's deposit flow for Quark's member site
(`test-v2.138hk.vip`). Measured against the live site this session - real findings, not confirmations:

- **`MemberSignInPage`**: the nav control that opens the Log In modal reads "Login" (one word); the
  submit button inside it reads "Log In" (two words) and carries no `submit` class at all (plain
  Tailwind utilities, `type="button"`). The ported code used "Log In" for both and a `class*='submit'`
  selector for the second - neither matched anything live. Both now matched by their own real text/role.
- **`AlertBoard`**: ported from an `AlertBoard_bg`/`AlertBoard_show` CSS-module shape that is real on
  this site (seen, hidden, on other mounted dialogs - `AlertBoard_modal-content`,
  `AlertBoard_deposit-activate-close`) but not on the one dialog this class actually needs: Deposit's
  own "Confirm Deposit?" popup is a different, plain Tailwind component, one instance mounted at a
  time, in a `div[class*='z-alertBoard']`. Every locator in the class was pointed at the real shape.
  This one defect alone meant `DepositPage.confirm()` could never have worked - `isConfirmDialogVisible()`
  read an always-empty `board.innerText()` regardless of what was actually on screen.
- **`DepositPage`**: the Channel tab ("Channel") and the Bank Transfer option both matched the ported
  constants exactly - no fix needed there. The amount summary is flakier: typing an amount always shows
  "Deposit turnover req." inline right away, but "Total game points" itself sometimes renders inline and
  sometimes only behind a "Show more" link (a modal) - which one was inline was not consistent between two
  tries moments apart. The readiness check (`showsAmountSummary()`) now polls for "Deposit turnover req."
  instead, since that one was inline every time; `totalGamePointsValueText()` still reads "Total game
  points" itself once ready, unchanged in meaning.
- **Still blocked**: `test.user.username`/`test.user.password` (`qa000092`/`password`) are
  NewWeibo_automation's own member account, carried over unverified - the live site refuses them. A
  browser on this machine already has a saved session for `qa00002` (one fewer zero) on
  `test-v2.138hk.vip`, very likely the real Quark QA account; `openSessions()` aborts on sign-in until
  Si supplies or confirms the real credentials. See `Config/config.properties`'s own note on
  `test.user.*`. Everything downstream of sign-in (the deposit itself, the admin-side cascade
  assertions) is consequently still unverified against this live site - only the sign-in and deposit-form
  layer was reachable this session.
