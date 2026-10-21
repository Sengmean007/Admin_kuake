# `docs/` — what a run produces

Everything under `docs/` is **generated**. Nothing here is written by hand and nothing here
needs editing: it is all rewritten at the end of every test run, from the test sources and
from the client's workbook. If something in these pages is wrong, the fix is in the code or
in the workbook, not in the file you are reading.

There are four kinds of output, and they answer different questions:

| | Question it answers | Written by |
| --- | --- | --- |
| `reports/` | How did this run go, case by case? | `HtmlReportGenerator` |
| `reports/extent/` | How did this run go, as a dashboard? | `ExtentReportListener` |
| `test-cases/` | Which of the client's rows do we automate, and can I run one? | `CaseSheetPage` |
| `reports/cases/` | The client's own workbook, with our results in it | `CaseSheetResults` |

Two further generated things live outside `docs/` and are produced by the same end-of-run
listener, so they are named here rather than left to be discovered: `TestCase/run/*.cmd`,
one double-clickable launcher per case, and — when `mail.enabled` is on — the email
`org.sengmean.report.ReportMailer` sends with the suite pages attached.

## `reports/` — generated test case reports

One HTML page per suite, plus `reports/index.html` tallying all of them. Columns are
**ID · test case · expected result · actual result · status · remark/cause · evidence**,
where evidence is the screenshot of the page as the case left it — taken for a pass as
well as a failure.

Every row also opens a **detail panel**, because seven columns is as wide as a report can
usefully be and everything that would not fit was being cut to a sentence. The panel holds
three things:

- **The case** as its source declares it — id, method, group, expected result, tags, and
  any `@Disabled` reason.
- **The result.** For a failure: the exception, the full message, expected against actual
  as a pair, the element the case was waiting on, every frame of the stack that belongs to
  this project, a plain-English reading of the failure class, and the full trace folded
  away. For a pass: what was verified, and how long it took. For a skip: which assumption
  did not hold.
- **What the run did** — the run's own record of the browser, recorded automatically while
  the case ran: `NAV` for a page load or a new tab, `API` for a call the site made of its
  own back end with the status it came back with, `NETWORK` for one that came back 4xx or
  5xx, `CONSOLE` for a console or uncaught error, `EVIDENCE` for a screenshot, and `STEP`,
  `CHECK` and `DATA` for whatever the case narrated itself.

A passing row carries a trail too, deliberately. A green result is a claim, and this is the
working behind it — the difference between a suite saying it approved a withdrawal and a
reader seeing `POST /api/withdrawal/approve -> 200` under the row that says so.

Above the tables: the failures listed once with a link into each row, a filter by status, a
search across id, name, tags and failure message, and a button that opens every panel at
once. The header carries the environment the run happened under — target, account, browser,
JUnit and JDK, machine, elapsed — and a bar showing the shape of the result.

These are **generated, not hand-written, and they rewrite themselves on every run**:

```bash
./mvnw test -DexcludedGroups=creates-data
```

That is the whole command. It used to carry `-Dheadless=true` as well; the browser window is
a setting in `Config/config.properties` now, headless by default, so a run only says so when
it wants the opposite — `-Dheadless=false` to watch one.

`org.sengmean.report.HtmlReportListener` is registered with the JUnit
Platform through `META-INF/services`, so it runs at the end of any launch — `./mvnw test`,
a single class from the IDE, one method from a run configuration — and rewrites these
pages before the JVM exits. There is no second command to remember, which was the point:
a report you have to ask for is a report that goes stale.

That one listener writes **everything a run produces**, in this order and for the same
reason each time:

1. the suite pages and `reports/index.html`
2. `test-cases/` — the client's rows, because which rows have a link depends on which ids
   the sources carry, and a case added today must not leave yesterday's links behind
3. `reports/cases/` — the client's workbook, from the same outcomes the pages were built
   from, so the two can never disagree about what happened
4. `TestCase/run/*.cmd` — the per-case launchers, from the same ids, so a case renamed in
   the IDE renames its launcher and a deleted one has its launcher pruned
5. the email, last, because it attaches what the four steps above just wrote — and only
   when `mail.enabled` says so, which it does not by default

Each is wrapped so that failing to write it cannot fail the run: these are by-products, and
a full disk or a mail server that is down must never be mistaken for a failing suite.

It runs **whether the suite passed or failed**. That is why it is a listener rather than
an `exec:java` execution bound to the `test` phase: Surefire fails the build the moment a
case goes red and Maven abandons the rest of the phase, so a phase binding would write the
report on green runs and skip it on exactly the runs worth reading.

The report describes **one run and only that run**. Its header says how many suites were
covered — "4 of 9 suites executed" after a package run — and every case the run did not
reach is listed as *Not run* rather than carried over from last time.

Case ids, expected results and tags come from the test sources; Surefire's XML carries
none of that, not even the `@DisplayName`. So a case renamed in code is renamed in the
report on the next run, and nothing here needs editing by hand.

The step trails come from `target/case-logs/`, written by `org.sengmean.report.CaseLog`
while each case runs and keyed exactly as the screenshots are. Nothing in the suites has to
call it: `BaseTest` attaches the listeners to every browser context the project opens, so a
new suite inherits a trail without being told to. Going through the filesystem is what lets
`exec:java@html-report` render the same detail as a live run.

`./mvnw exec:java@html-report` still rebuilds the pages from Surefire XML without re-running
anything. Treat it as a fallback: Surefire never clears its results directory, so after a
one-package run that XML is a blend of that package's fresh results and whatever the last
full run left behind, stamped with a single time. The listener has no such problem.

`-DexcludedGroups=creates-data` keeps out the cases whose effects outlive the run: an
approved or rejected transaction, a created or edited member, a changed setting another
suite reads. Those cases still appear in the report, as **Not run**, so the gap is visible
rather than silent. Run them attended if you want them green.

`known-defect` cases are deliberately **left in** the run. They assert the behaviour the
application *should* have, so a red one is the expected outcome and the report says so in
its remark column.

### `reports/.state/` — one properties file per suite

Not a report and not worth opening. Each file holds a suite's counts and run time from the
last time that suite's page was written, and `index.html` reads them back for the suites
this run did not touch. Without it, running one package would leave the index claiming
"0 cases" for the other nine — which reads as nine broken suites rather than as nine that
were not asked to run. It is the one thing here that deliberately outlives a run.

### `reports/screenshots/` — the evidence column

The page as each case left it, taken for a pass as well as a failure, keyed `Suite.method`.
Cleared at the start of every run: a case that failed yesterday and passes today must not
keep yesterday's picture under its green row, which is worse than having no picture at all.

## `reports/extent/` — the Extent Spark dashboard

`reports/extent/index.html`, written by `org.sengmean.report.ExtentReportListener`. One page
for the whole run, with the pass/fail chart, the category filters and the full-text search a
standard tool gives for free.

It answers a different question from the pages above and is not a substitute for them. The
`reports/` pages are the **client-facing** deliverable, laid out in the columns the test case
workbook uses because they are meant to be read next to it. Extent answers *what did this
run do*. Both are built from the same evidence — the same step trails, the same screenshots,
the same case ids — so they cannot disagree about what happened.

It keeps its own copy of the screenshots under `extent/screenshots/`. The two listeners both
fire on `testPlanExecutionFinished` and the JUnit Platform does not order them, so neither
may depend on what the other wrote; some duplicated PNGs is the price of that independence.

## `test-cases/` — the client's rows, with a Run link

`test-cases/index.html` and one page per feature, written by
`org.sengmean.report.CaseSheetPage` from `src/test/resources/testdata/client_testcase.xlsx`.

Every row of every tab, in the client's own words, with a link on the rows that have a test
behind them. It exists because reading the workbook and running the suite otherwise meant
keeping a spreadsheet and an IDE open and knowing which Java method answered which row.

**A row with no link is a row with no test behind it, and that is information.** Linking
every row and letting the run report `Tests run: 0` would hide exactly the thing this page is
for. The link goes through the `awb://` scheme — register it once with
`tools/install-url-handler.ps1` — or use the `.cmd` of the same name in `TestCase/run/`,
which needs nothing installed.

The workbook is a **copy** of the client's Google Sheet, checked in so the project can be
read and run without an account on someone else's Drive. Nothing writes back to it.

**Re-exporting takes two commands, in this order:**

```bash
./mvnw exec:java@trim-cases     # removes the TC_TP_ rows, which are out of scope
./mvnw exec:java@split-cases    # rebuilds the per-feature workbooks
```

The trim is not optional and not a one-off. The 246 `TC_TP_` rows were deleted from this copy
on 2026-09-11, but they are still in the client's original and always will be, so a fresh
export brings every one of them back — silently, with no error to notice, just 146 extra rows
on the green tabs. Run `trim-cases` first, before anything reads the workbook. It is safe to
run twice.

## `reports/cases/` — the client's workbook with our results in it

One `.xlsx` per feature, written by `org.sengmean.report.CaseSheetResults`: the client's own
rows, with a Run link on every row a test answers and this run's outcome in its **STATUS**
and **ACTUAL RESULT** columns. This is the thing to send when someone asks for results in the
format they gave us.

**Gitignored, along with `reports/client_testcase-results.xlsx`.** Together about 8 MB of
binary, all of it rebuilt from what is already tracked, and committing it would put a diff
nobody can read into the history every time anyone ran a single case. The HTML beside it
stays tracked because its diffs can be read. Rebuild without running a suite with
`./mvnw exec:java@case-results`.
