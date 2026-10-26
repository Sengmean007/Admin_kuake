# TestCase/run

One launcher per automated case. Double-click the `.cmd` for the case you want and
it runs on its own, against the environment `Config/config.properties` names.

**Everything in this folder is generated** by `org.sengmean.testcase.CaseRunnerGenerator`
and rewritten at the end of every test run, so it cannot drift from the suites. Edits
here are lost. Rebuild it without running anything with:

```
./mvnw exec:java@case-runners
```

Two tags change what a launcher does before it runs anything:

- **creates-data** - the case leaves something behind on the test environment that
  somebody may have to undo. Its launcher says so and waits for a keypress first.
- **known-defect** - the case asserts behaviour the site does not have yet, so a red
  result is expected. Its launcher says so.

| Case | Suite | What it proves | |
|---|---|---|---|
| [`ADMLOGIN-001`](ADMLOGIN-001.cmd) | AdminLoginTest | a valid operator account signs in and reaches the Control Panel |  |
| [`BO-ADD-FRM-001`](BO-ADD-FRM-001.cmd) | AddMemberResetPasswordTest | Add Member form opens with all fields in default state | known-defect |
| [`BO-ADD-FRM-002`](BO-ADD-FRM-002.cmd) | AddMemberResetPasswordTest | Password and Confirm password are masked by default |  |
| [`BO-ADD-FRM-003`](BO-ADD-FRM-003.cmd) | AddMemberResetPasswordTest | Helper text shows the rule list when the field is empty | known-defect |
| [`BO-ADD-FRM-004`](BO-ADD-FRM-004.cmd) | AddMemberResetPasswordTest | Show icon on Password reveals the typed value | known-defect |
| [`BO-ADD-FRM-005`](BO-ADD-FRM-005.cmd) | AddMemberResetPasswordTest | Hide icon on Password masks the value again | known-defect |
| [`BO-ADD-FRM-006`](BO-ADD-FRM-006.cmd) | AddMemberResetPasswordTest | Show icon on Confirm password reveals its own value | known-defect |
| [`BO-ADD-FRM-007`](BO-ADD-FRM-007.cmd) | AddMemberResetPasswordTest | Password show icon does not affect Confirm password | known-defect |
| [`BO-ADD-FRM-008`](BO-ADD-FRM-008.cmd) | AddMemberResetPasswordTest | Confirm password show icon does not affect Password | known-defect |
| [`BO-ADD-FRM-009`](BO-ADD-FRM-009.cmd) | AddMemberResetPasswordTest | Toggling visibility keeps the typed value and rule state | known-defect |
| [`BO-ADD-FRM-010`](BO-ADD-FRM-010.cmd) | AddMemberResetPasswordTest | Typing while visible continues to update the checklist | known-defect |
| [`BO-ADD-FRM-011`](BO-ADD-FRM-011.cmd) | AddMemberResetPasswordTest | Country code dropdown defaults to HK +852 |  |
| [`BO-ADD-FRM-012`](BO-ADD-FRM-012.cmd) | AddMemberResetPasswordTest | Admin can change the country code |  |
| [`BO-ADD-FRM-013`](BO-ADD-FRM-013.cmd) | AddMemberResetPasswordTest | No OTP / verification code is requested in Back Office |  |
| [`BO-ADD-FRM-014`](BO-ADD-FRM-014.cmd) | AddMemberResetPasswordTest | Username required rule (OQ-01) |  |
| [`BO-ADD-FRM-015`](BO-ADD-FRM-015.cmd) | AddMemberResetPasswordTest | Full name required rule (OQ-02) |  |
| [`BO-ADD-FRM-016`](BO-ADD-FRM-016.cmd) | AddMemberResetPasswordTest | Email required / format rule (OQ-03) |  |
| [`BO-ADD-PWD-001`](BO-ADD-PWD-001.cmd) | AddMemberResetPasswordTest | 7 characters fails the length rule | known-defect |
| [`BO-ADD-PWD-002`](BO-ADD-PWD-002.cmd) | AddMemberResetPasswordTest | 8 characters passes the length rule | known-defect |
| [`BO-ADD-PWD-003`](BO-ADD-PWD-003.cmd) | AddMemberResetPasswordTest | 20 characters passes the length rule | known-defect |
| [`BO-ADD-PWD-004`](BO-ADD-PWD-004.cmd) | AddMemberResetPasswordTest | 21 characters fails the length rule (OQ-06) | known-defect |
| [`BO-ADD-PWD-005`](BO-ADD-PWD-005.cmd) | AddMemberResetPasswordTest | Password without uppercase fails | known-defect |
| [`BO-ADD-PWD-006`](BO-ADD-PWD-006.cmd) | AddMemberResetPasswordTest | One uppercase letter satisfies the rule | known-defect |
| [`BO-ADD-PWD-007`](BO-ADD-PWD-007.cmd) | AddMemberResetPasswordTest | Password without lowercase fails | known-defect |
| [`BO-ADD-PWD-008`](BO-ADD-PWD-008.cmd) | AddMemberResetPasswordTest | One lowercase letter satisfies the rule | known-defect |
| [`BO-ADD-PWD-009`](BO-ADD-PWD-009.cmd) | AddMemberResetPasswordTest | Password without a number fails | known-defect |
| [`BO-ADD-PWD-010`](BO-ADD-PWD-010.cmd) | AddMemberResetPasswordTest | One digit satisfies the number rule | known-defect |
| [`BO-ADD-PWD-011`](BO-ADD-PWD-011.cmd) | AddMemberResetPasswordTest | Leading space fails the no-spaces rule | known-defect |
| [`BO-ADD-PWD-012`](BO-ADD-PWD-012.cmd) | AddMemberResetPasswordTest | Middle space fails the no-spaces rule | known-defect |
| [`BO-ADD-PWD-013`](BO-ADD-PWD-013.cmd) | AddMemberResetPasswordTest | Trailing space fails the no-spaces rule | known-defect |
| [`BO-ADD-PWD-014`](BO-ADD-PWD-014.cmd) | AddMemberResetPasswordTest | Password with no spaces passes | known-defect |
| [`BO-ADD-PWD-015`](BO-ADD-PWD-015.cmd) | AddMemberResetPasswordTest | Password meeting all 5 rules shows every rule green and strength Good | known-defect |
| [`BO-ADD-PWD-016`](BO-ADD-PWD-016.cmd) | AddMemberResetPasswordTest | Special characters are allowed when other rules are met (OQ-07) | known-defect |
| [`BO-ADD-PWD-017`](BO-ADD-PWD-017.cmd) | AddMemberResetPasswordTest | Rules match front-end registration |  |
| [`BO-ADD-CHK-001`](BO-ADD-CHK-001.cmd) | AddMemberResetPasswordTest | Checklist appears when the admin starts typing | known-defect |
| [`BO-ADD-CHK-002`](BO-ADD-CHK-002.cmd) | AddMemberResetPasswordTest | Checklist updates on every keystroke | known-defect |
| [`BO-ADD-CHK-003`](BO-ADD-CHK-003.cmd) | AddMemberResetPasswordTest | Checklist updates when characters are deleted | known-defect |
| [`BO-ADD-CHK-004`](BO-ADD-CHK-004.cmd) | AddMemberResetPasswordTest | Clearing the field returns to the default state (OQ-08) | known-defect |
| [`BO-ADD-CHK-005`](BO-ADD-CHK-005.cmd) | AddMemberResetPasswordTest | Pasting a password updates the checklist | known-defect |
| [`BO-ADD-CHK-006`](BO-ADD-CHK-006.cmd) | AddMemberResetPasswordTest | Weak label for a short lowercase password | known-defect |
| [`BO-ADD-CHK-007`](BO-ADD-CHK-007.cmd) | AddMemberResetPasswordTest | Medium label when only the number rule is missing | known-defect |
| [`BO-ADD-CHK-008`](BO-ADD-CHK-008.cmd) | AddMemberResetPasswordTest | Good label when all 5 rules are met | known-defect |
| [`BO-ADD-CHK-009`](BO-ADD-CHK-009.cmd) | AddMemberResetPasswordTest | Label moves Weak to Medium to Good while typing | known-defect |
| [`BO-ADD-CHK-010`](BO-ADD-CHK-010.cmd) | AddMemberResetPasswordTest | Label moves down when a character is deleted (OQ-05) | known-defect |
| [`BO-ADD-CHK-011`](BO-ADD-CHK-011.cmd) | AddMemberResetPasswordTest | A Medium password never enables Add member | known-defect |
| [`BO-ADD-CHK-012`](BO-ADD-CHK-012.cmd) | AddMemberResetPasswordTest | Strength label colour and position match the design | known-defect |
| [`BO-ADD-BTN-001`](BO-ADD-BTN-001.cmd) | AddMemberResetPasswordTest | Matching passwords show no error |  |
| [`BO-ADD-BTN-002`](BO-ADD-BTN-002.cmd) | AddMemberResetPasswordTest | Mismatched passwords show an error | known-defect |
| [`BO-ADD-BTN-003`](BO-ADD-BTN-003.cmd) | AddMemberResetPasswordTest | Match is case-sensitive | known-defect |
| [`BO-ADD-BTN-004`](BO-ADD-BTN-004.cmd) | AddMemberResetPasswordTest | Changing Password after a match re-checks the match | known-defect |
| [`BO-ADD-BTN-005`](BO-ADD-BTN-005.cmd) | AddMemberResetPasswordTest | Fixing Confirm password clears the error | known-defect |
| [`BO-ADD-BTN-006`](BO-ADD-BTN-006.cmd) | AddMemberResetPasswordTest | Trailing space in Confirm causes a mismatch | known-defect |
| [`BO-ADD-BTN-007`](BO-ADD-BTN-007.cmd) | AddMemberResetPasswordTest | Add member is disabled in the default (empty) state | known-defect |
| [`BO-ADD-BTN-008`](BO-ADD-BTN-008.cmd) | AddMemberResetPasswordTest | Add member is disabled in the Weak state | known-defect |
| [`BO-ADD-BTN-009`](BO-ADD-BTN-009.cmd) | AddMemberResetPasswordTest | Add member is disabled in the Medium state | known-defect |
| [`BO-ADD-BTN-010`](BO-ADD-BTN-010.cmd) | AddMemberResetPasswordTest | Add member is disabled when Good but not matching | known-defect |
| [`BO-ADD-BTN-011`](BO-ADD-BTN-011.cmd) | AddMemberResetPasswordTest | Add member enables only when Good and matching (Ready) | known-defect |
| [`BO-ADD-BTN-012`](BO-ADD-BTN-012.cmd) | AddMemberResetPasswordTest | Button disables again when the Ready state is broken | known-defect |
| [`BO-ADD-BTN-013`](BO-ADD-BTN-013.cmd) | AddMemberResetPasswordTest | Enter key does not submit while disabled | known-defect |
| [`BO-ADD-AFT-001`](BO-ADD-AFT-001.cmd) | AddMemberResetPasswordTest | A member is created with valid data | creates-data |
| [`BO-ADD-AFT-002`](BO-ADD-AFT-002.cmd) | AddMemberResetPasswordTest | New member status is Active |  |
| [`BO-ADD-AFT-003`](BO-ADD-AFT-003.cmd) | AddMemberResetPasswordTest | Admin returns to Member List with the new member shown |  |
| [`BO-ADD-AFT-004`](BO-ADD-AFT-004.cmd) | AddMemberResetPasswordTest | Admin is not logged in as the new member |  |
| [`BO-ADD-AFT-005`](BO-ADD-AFT-005.cmd) | AddMemberResetPasswordTest | No OTP is sent to the new member's phone |  |
| [`BO-ADD-AFT-006`](BO-ADD-AFT-006.cmd) | AddMemberResetPasswordTest | New member can log in with the admin-set password |  |
| [`BO-ADD-AFT-007`](BO-ADD-AFT-007.cmd) | AddMemberResetPasswordTest | Double-click on Add member creates only one member | creates-data |
| [`BO-RST-001`](BO-RST-001.cmd) | AddMemberResetPasswordTest | New password and Confirm are masked by default |  |
| [`BO-RST-901`](BO-RST-901.cmd) | AddMemberResetPasswordTest | Helper text is shown and Save is disabled by default (GD-198) | known-defect |
| [`BO-RST-002`](BO-RST-002.cmd) | AddMemberResetPasswordTest | Each show/hide icon toggles only its own field | known-defect |
| [`BO-RST-003`](BO-RST-003.cmd) | AddMemberResetPasswordTest | Checklist and strength label update on every keystroke | known-defect |
| [`BO-RST-004`](BO-RST-004.cmd) | AddMemberResetPasswordTest | Length boundaries 7/8/20/21 | known-defect |
| [`BO-RST-005`](BO-RST-005.cmd) | AddMemberResetPasswordTest | Space at start, middle or end fails | known-defect |
| [`BO-RST-006`](BO-RST-006.cmd) | AddMemberResetPasswordTest | Save disabled for Weak | known-defect |
| [`BO-RST-007`](BO-RST-007.cmd) | AddMemberResetPasswordTest | Save disabled for Medium | known-defect |
| [`BO-RST-008`](BO-RST-008.cmd) | AddMemberResetPasswordTest | Save disabled when Good but not matching | known-defect |
| [`BO-RST-009`](BO-RST-009.cmd) | AddMemberResetPasswordTest | Save enabled only when Good and matching | known-defect |
| [`BO-RST-010`](BO-RST-010.cmd) | AddMemberResetPasswordTest | Editing New password after a match re-checks the match | known-defect |
| [`BO-RST-011`](BO-RST-011.cmd) | AddMemberResetPasswordTest | Password updated, success message, admin stays on the member profile | creates-data |
| [`BO-RST-012`](BO-RST-012.cmd) | AddMemberResetPasswordTest | Member can log in with the new password only |  |
| [`BO-RST-013`](BO-RST-013.cmd) | AddMemberResetPasswordTest | Reset form uses the same checklist/label/match components as Add Member | known-defect |
| [`BO-API-001`](BO-API-001.cmd) | AddMemberResetPasswordTest | Valid request creates the member |  |
| [`BO-API-002`](BO-API-002.cmd) | AddMemberResetPasswordTest | Weak password sent directly is refused |  |
| [`BO-API-003`](BO-API-003.cmd) | AddMemberResetPasswordTest | Medium password sent directly is refused |  |
| [`BO-API-004`](BO-API-004.cmd) | AddMemberResetPasswordTest | Mismatched passwords are refused |  |
| [`BO-API-005`](BO-API-005.cmd) | AddMemberResetPasswordTest | Boundary lengths 7 and 21 are refused |  |
| [`BO-API-006`](BO-API-006.cmd) | AddMemberResetPasswordTest | Password with a space is refused |  |
| [`BO-API-007`](BO-API-007.cmd) | AddMemberResetPasswordTest | Error lists every failed rule |  |
| [`BO-API-008`](BO-API-008.cmd) | AddMemberResetPasswordTest | Password is never returned in any response |  |
| [`BO-API-009`](BO-API-009.cmd) | AddMemberResetPasswordTest | Password is stored hashed |  |
| [`BO-API-010`](BO-API-010.cmd) | AddMemberResetPasswordTest | Valid reset request updates the password |  |
| [`BO-API-011`](BO-API-011.cmd) | AddMemberResetPasswordTest | Weak password sent directly to reset is refused |  |
| [`BO-API-012`](BO-API-012.cmd) | AddMemberResetPasswordTest | Mismatched reset passwords are refused |  |
| [`BO-API-013`](BO-API-013.cmd) | AddMemberResetPasswordTest | Password not returned in reset response |  |
| [`BO-API-014`](BO-API-014.cmd) | AddMemberResetPasswordTest | Same validator as front-end registration |  |
| [`RANK-DAT-001`](RANK-DAT-001.cmd) | RankingTest | Settled vs non-settled commission |  |
| [`RANK-DAT-002`](RANK-DAT-002.cmd) | RankingTest | Reversed and cancelled commission |  |
| [`RANK-DAT-003`](RANK-DAT-003.cmd) | RankingTest | Successful vs failed withdrawal |  |
| [`RANK-DAT-004`](RANK-DAT-004.cmd) | RankingTest | Pending, rejected and cancelled withdrawals excluded |  |
| [`RANK-DAT-005`](RANK-DAT-005.cmd) | RankingTest | Referral count column |  |
| [`RANK-DAT-006`](RANK-DAT-006.cmd) | RankingTest | Sort order, highest first |  |
| [`RANK-DAT-007`](RANK-DAT-007.cmd) | RankingTest | Tie-breaker on equal amounts (OQ-12, answered in general by Ranking Settings) |  |
| [`RANK-DAT-008`](RANK-DAT-008.cmd) | RankingTest | Tie-breaker when the earlier member later dropped and regained the amount |  |
| [`RANK-DAT-009`](RANK-DAT-009.cmd) | RankingTest | $0 accounts show Not ranked yet (OQ-11) |  |
| [`RANK-DAT-010`](RANK-DAT-010.cmd) | RankingTest | Finance correction after snapshot changes only at the next update |  |
| [`RANK-DAT-011`](RANK-DAT-011.cmd) | RankingTest | One snapshot per member per update |  |
| [`RANK-DAT-012`](RANK-DAT-012.cmd) | RankingTest | Launch backfill matches finance |  |
| [`RANK-DAT-013`](RANK-DAT-013.cmd) | RankingTest | No manual amount entry |  |
| [`RANK-SCH-001`](RANK-SCH-001.cmd) | RankingTest | Daily update lands on 00:00 |  |
| [`RANK-SCH-002`](RANK-SCH-002.cmd) | RankingTest | Hourly update lands on the hour |  |
| [`RANK-SCH-003`](RANK-SCH-003.cmd) | RankingTest | A transaction made between updates is not reflected until the next one |  |
| [`RANK-SCH-004`](RANK-SCH-004.cmd) | RankingTest | A failed scheduled update keeps the previous snapshot live |  |
| [`RANK-SCH-005`](RANK-SCH-005.cmd) | RankingTest | Atomic snapshot publish under load |  |
| [`RANK-SCH-006`](RANK-SCH-006.cmd) | RankingTest | Timestamps are internally consistent |  |
| [`RANK-ADM-LST-001`](RANK-ADM-LST-001.cmd) | RankingTest | Rankings menu opens with 3 tabs |  |
| [`RANK-ADM-LST-002`](RANK-ADM-LST-002.cmd) | RankingTest | Status bar content (AD-01) |  |
| [`RANK-ADM-LST-003`](RANK-ADM-LST-003.cmd) | RankingTest | Column layout (AD-03, see the class Javadoc for the mock vs sheet gap) |  |
| [`RANK-ADM-LST-004`](RANK-ADM-LST-004.cmd) | RankingTest | Admin shows exact amounts and full usernames regardless of client display (AD-06) |  |
| [`RANK-ADM-LST-005`](RANK-ADM-LST-005.cmd) | RankingTest | Search by username returns only that row |  |
| [`RANK-ADM-LST-006`](RANK-ADM-LST-006.cmd) | RankingTest | Search by member ID returns only that row |  |
| [`RANK-ADM-LST-007`](RANK-ADM-LST-007.cmd) | RankingTest | Search with no match shows No data |  |
| [`RANK-ADM-LST-008`](RANK-ADM-LST-008.cmd) | RankingTest | Pagination and Show 50 entries (AD-04) |  |
| [`RANK-ADM-LST-009`](RANK-ADM-LST-009.cmd) | RankingTest | Export CSV headers match list columns |  |
| [`RANK-ADM-LST-010`](RANK-ADM-LST-010.cmd) | RankingTest | Export CSV with search applied (OQ-13) |  |
| [`RANK-ADM-LST-011`](RANK-ADM-LST-011.cmd) | RankingTest | No per-member editing |  |
| [`RANK-ADM-LST-012`](RANK-ADM-LST-012.cmd) | RankingTest | Empty state shows No data |  |
| [`RANK-ADM-LST-013`](RANK-ADM-LST-013.cmd) | RankingTest | List loads within 3 seconds (NF-01, target to confirm) |  |
| [`RANK-ADM-TGL-001`](RANK-ADM-TGL-001.cmd) | RankingTest | Turning Withdrawal Ranking off |  |
| [`RANK-ADM-TGL-002`](RANK-ADM-TGL-002.cmd) | RankingTest | Turning Referral Ranking off |  |
| [`RANK-ADM-TGL-003`](RANK-ADM-TGL-003.cmd) | RankingTest | Both toggles off |  |
| [`RANK-ADM-TGL-004`](RANK-ADM-TGL-004.cmd) | RankingTest | Toggles are independent (AD-02) |  |
| [`RANK-ADM-TGL-005`](RANK-ADM-TGL-005.cmd) | RankingTest | Toggle back on |  |
| [`RANK-ADM-TGL-006`](RANK-ADM-TGL-006.cmd) | RankingTest | Toggle off applies to all currencies (AD-19) |  |
| [`RANK-ADM-HIS-001`](RANK-ADM-HIS-001.cmd) | RankingTest | Opening the modal (AD-07, AD-08) |  |
| [`RANK-ADM-HIS-002`](RANK-ADM-HIS-002.cmd) | RankingTest | Summary values are consistent with the list (AD-09) |  |
| [`RANK-ADM-HIS-003`](RANK-ADM-HIS-003.cmd) | RankingTest | Snapshot table order and columns (AC-07, AD-10) |  |
| [`RANK-ADM-HIS-004`](RANK-ADM-HIS-004.cmd) | RankingTest | Rank Change markers |  |
| [`RANK-ADM-HIS-005`](RANK-ADM-HIS-005.cmd) | RankingTest | Amount Change is the difference between consecutive snapshots |  |
| [`RANK-ADM-HIS-006`](RANK-ADM-HIS-006.cmd) | RankingTest | Period filter (Today / Last 7 days / Last 30 days) |  |
| [`RANK-ADM-HIS-007`](RANK-ADM-HIS-007.cmd) | RankingTest | Export CSV from the modal |  |
| [`RANK-ADM-HIS-008`](RANK-ADM-HIS-008.cmd) | RankingTest | Close via ✕ and via Close |  |
| [`RANK-ADM-HIS-009`](RANK-ADM-HIS-009.cmd) | RankingTest | A member with only one snapshot |  |
| [`RANK-ADM-SET-001`](RANK-ADM-SET-001.cmd) | RankingTest | Locked fixed settings cannot be edited (AC-14) |  |
| [`RANK-ADM-SET-002`](RANK-ADM-SET-002.cmd) | RankingTest | Default Amount display is Rounded |  |
| [`RANK-ADM-SET-003`](RANK-ADM-SET-003.cmd) | RankingTest | Client preview updates per option (AD-13) |  |
| [`RANK-ADM-SET-004`](RANK-ADM-SET-004.cmd) | RankingTest | Save changes the client display (AD-13, AD-14) |  |
| [`RANK-ADM-SET-005`](RANK-ADM-SET-005.cmd) | RankingTest | Cancel leaves the setting unchanged |  |
| [`RANK-ADM-SET-006`](RANK-ADM-SET-006.cmd) | RankingTest | Operation log entry for a display toggle change (AC-06, AD-15) |  |
| [`RANK-ADM-SET-007`](RANK-ADM-SET-007.cmd) | RankingTest | Operation log entry for an Amount display change |  |
| [`RANK-ADM-SET-008`](RANK-ADM-SET-008.cmd) | RankingTest | Cancel with no save writes no log entry |  |
| [`RANK-ADM-SET-009`](RANK-ADM-SET-009.cmd) | RankingTest | Admin without Rankings permission (NF-04) |  |
| [`RANK-ADM-CUR-001`](RANK-ADM-CUR-001.cmd) | RankingTest | Currency selector changes the list (AD-16) |  |
| [`RANK-ADM-CUR-002`](RANK-ADM-CUR-002.cmd) | RankingTest | Amount header carries the currency (AD-17) |  |
| [`RANK-ADM-CUR-003`](RANK-ADM-CUR-003.cmd) | RankingTest | Export CSV reflects the selected currency (AC-17) |  |
| [`RANK-ADM-CUR-004`](RANK-ADM-CUR-004.cmd) | RankingTest | History shows the selected currency (AD-18) |  |
| [`RANK-ADM-CUR-005`](RANK-ADM-CUR-005.cmd) | RankingTest | Ranks are separate per currency, not combined or converted (AC-15, MC-04) |  |
| [`RANK-ADM-CUR-006`](RANK-ADM-CUR-006.cmd) | RankingTest | A wallet with no activity (MC-05) |  |
| [`OC-CRT-902`](OC-CRT-902.cmd) | OtherCategorySeedData | Seed 20 Other Categories in one shot, kept for the data-heavy rows | creates-data |
| [`OC-DEL-901`](OC-DEL-901.cmd) | OtherCategorySeedData | Delete every seeded Other Category | creates-data |
| [`OC-ACC-001`](OC-ACC-001.cmd) | OtherCategoryTest | Super Admin can Create, Edit, Delete categories and manage games/banners | creates-data |
| [`OC-ACC-002`](OC-ACC-002.cmd) | OtherCategoryTest | Admin attempts the same actions as Super Admin | creates-data |
| [`OC-ACC-003`](OC-ACC-003.cmd) | OtherCategoryTest | Agent role cannot access Other Category management |  |
| [`OC-ACC-004`](OC-ACC-004.cmd) | OtherCategoryTest | Ordinary user cannot access Other Category management |  |
| [`OC-ACC-005`](OC-ACC-005.cmd) | OtherCategoryTest | Direct deep-link to Create/Edit/Manage Games URLs without the proper role |  |
| [`OC-CRT-001`](OC-CRT-001.cmd) | OtherCategoryTest | Create succeeds with a complete, valid category | creates-data |
| [`OC-CRT-002`](OC-CRT-002.cmd) | OtherCategoryTest | Creating a category while a language tab's name is empty | creates-data |
| [`OC-CRT-003`](OC-CRT-003.cmd) | OtherCategoryTest | The Category Name input shows and edits only the currently selected language tab |  |
| [`OC-CRT-004`](OC-CRT-004.cmd) | OtherCategoryTest | Entering a name on one tab and switching away does not lose it |  |
| [`OC-CRT-005`](OC-CRT-005.cmd) | OtherCategoryTest | Uploading a PNG file for the category icon |  |
| [`OC-CRT-006`](OC-CRT-006.cmd) | OtherCategoryTest | Uploading a JPG file, then an SVG file, for the category icon |  |
| [`OC-CRT-007`](OC-CRT-007.cmd) | OtherCategoryTest | Attempting to upload a GIF or PDF as the category icon |  |
| [`OC-CRT-008`](OC-CRT-008.cmd) | OtherCategoryTest | Creating a category with no icon selected | creates-data |
| [`OC-CRT-009`](OC-CRT-009.cmd) | OtherCategoryTest | Entering letters or symbols into the Display order field |  |
| [`OC-CRT-010`](OC-CRT-010.cmd) | OtherCategoryTest | Creating a category with the same Order as an existing one | creates-data |
| [`OC-CRT-901`](OC-CRT-901.cmd) | OtherCategoryTest | Creating a category with the same name as an existing one | creates-data |
| [`OC-CRT-011`](OC-CRT-011.cmd) | OtherCategoryTest | Clicking Save & manage games opens Manage Games for the newly created category | creates-data |
| [`OC-CRT-012`](OC-CRT-012.cmd) | OtherCategoryTest | Clicking Cancel during Create does not save anything |  |
| [`OC-LST-001`](OC-LST-001.cmd) | OtherCategoryTest | Category list shows icon, name, games count, status, and order for every category | creates-data |
| [`OC-LST-002`](OC-LST-002.cmd) | OtherCategoryTest | Search returns only categories matching the entered text |  |
| [`OC-LST-003`](OC-LST-003.cmd) | OtherCategoryTest | Status filter shows only Active or only Inactive categories as selected | creates-data |
| [`OC-LST-004`](OC-LST-004.cmd) | OtherCategoryTest | Changing the list's language filter updates the Category name column to that language's translation |  |
| [`OC-LST-005`](OC-LST-005.cmd) | OtherCategoryTest | Page-size selector controls how many categories are listed at once | creates-data |
| [`OC-LST-006`](OC-LST-006.cmd) | OtherCategoryTest | Previous/Next controls navigate between pages of categories | creates-data |
| [`OC-LST-007`](OC-LST-007.cmd) | OtherCategoryTest | The Games column matches the actual number of games assigned via Manage Games | creates-data |
| [`OC-LST-008`](OC-LST-008.cmd) | OtherCategoryTest | Categories are listed in ascending Display Order |  |
| [`OC-GAM-001`](OC-GAM-001.cmd) | OtherCategoryTest | Selecting an unselected game increments "N games selected" |  |
| [`OC-GAM-002`](OC-GAM-002.cmd) | OtherCategoryTest | Deselecting a previously selected game decrements the counter | creates-data |
| [`OC-GAM-003`](OC-GAM-003.cmd) | OtherCategoryTest | Search returns only games matching the entered text |  |
| [`OC-GAM-004`](OC-GAM-004.cmd) | OtherCategoryTest | The "All games" filter narrows the list to a chosen game |  |
| [`OC-GAM-005`](OC-GAM-005.cmd) | OtherCategoryTest | The "All providers" filter narrows the list to a chosen provider |  |
| [`OC-GAM-901`](OC-GAM-901.cmd) | OtherCategoryTest | Filtering selected games with no matching result shows an empty table with all its headers |  |
| [`OC-GAM-011`](OC-GAM-011.cmd) | OtherCategoryTest | Searching games with no match ("sss") shows every table header on one line, without breaking |  |
| [`OC-GAM-012`](OC-GAM-012.cmd) | OtherCategoryTest | Saving with no games selected is not allowed | creates-data |
| [`OC-GAM-013`](OC-GAM-013.cmd) | OtherCategoryTest | Double-clicking Save assignments sends only one save request | creates-data |
| [`OC-GAM-014`](OC-GAM-014.cmd) | OtherCategoryTest | Search ignores letter case - "horse", "HORSE" and "Horse" find the same games |  |
| [`OC-GAM-015`](OC-GAM-015.cmd) | OtherCategoryTest | Search finds text in the middle of a game name |  |
| [`OC-GAM-016`](OC-GAM-016.cmd) | OtherCategoryTest | Spaces around a search are ignored, and a search of spaces only lists every game |  |
| [`OC-GAM-017`](OC-GAM-017.cmd) | OtherCategoryTest | Special characters in the search give a plain result, with no error and no script run |  |
| [`OC-GAM-018`](OC-GAM-018.cmd) | OtherCategoryTest | Search, Provider and Selection filters combine - every game shown matches all three |  |
| [`OC-GAM-019`](OC-GAM-019.cmd) | OtherCategoryTest | A selected game stays selected after searching for something else and clearing the search |  |
| [`OC-GAM-020`](OC-GAM-020.cmd) | OtherCategoryTest | The "N games selected" counter equals the number of games under the Selected filter |  |
| [`OC-GAM-021`](OC-GAM-021.cmd) | OtherCategoryTest | Scrolling to the end of the list loads every game exactly once |  |
| [`OC-GAM-022`](OC-GAM-022.cmd) | OtherCategoryTest | The longest game name keeps the table aligned - headers unbroken and no column pushed off the side |  |
| [`OC-GAM-023`](OC-GAM-023.cmd) | OtherCategoryTest | In narrower windows (1280 and 1024 wide) every header still reads on one line |  |
| [`OC-GAM-006`](OC-GAM-006.cmd) | OtherCategoryTest | A game already assigned to one category can still be selected in a different category | creates-data |
| [`OC-GAM-007`](OC-GAM-007.cmd) | OtherCategoryTest | Attempting to Save with the "1 selected game still needs a banner" warning showing | creates-data |
| [`OC-GAM-008`](OC-GAM-008.cmd) | OtherCategoryTest | Save completes when the banner-required warning is fully cleared | creates-data |
| [`OC-GAM-009`](OC-GAM-009.cmd) | OtherCategoryTest | Deselecting a game that already has an uploaded banner | creates-data |
| [`OC-GAM-010`](OC-GAM-010.cmd) | OtherCategoryTest | Selecting/deselecting games and clicking Cancel does not save the changes | creates-data |
| [`OC-BAN-001`](OC-BAN-001.cmd) | OtherCategoryTest | Uploading a WEBP banner for a selected game |  |
| [`OC-BAN-002`](OC-BAN-002.cmd) | OtherCategoryTest | Uploading a JPG banner for a selected game |  |
| [`OC-BAN-901`](OC-BAN-901.cmd) | OtherCategoryTest | Uploading a generated SVG banner for a selected game |  |
| [`OC-BAN-902`](OC-BAN-902.cmd) | OtherCategoryTest | Uploading a generated WEBP banner for a selected game |  |
| [`OC-BAN-003`](OC-BAN-003.cmd) | OtherCategoryTest | Attempting to upload a PNG or GIF as a game banner |  |
| [`OC-BAN-004`](OC-BAN-004.cmd) | OtherCategoryTest | Uploading a banner far from the recommended 4:3 ratio |  |
| [`OC-BAN-005`](OC-BAN-005.cmd) | OtherCategoryTest | Clicking Replace banner on a game that already has one |  |
| [`OC-BAN-006`](OC-BAN-006.cmd) | OtherCategoryTest | Uploading a banner for a game in one category does not change that same game's banner in a different category |  |
| [`OC-BAN-007`](OC-BAN-007.cmd) | OtherCategoryTest | Selecting a file and clicking Cancel does not save the banner |  |
| [`OC-BAN-008`](OC-BAN-008.cmd) | OtherCategoryTest | Dragging a valid file onto the drop zone behaves the same as "choose a file" |  |
| [`OC-BAN-009`](OC-BAN-009.cmd) | OtherCategoryTest | Attempting to upload a banner file above the maximum accepted size |  |
| [`OC-BAN-010`](OC-BAN-010.cmd) | OtherCategoryTest | The per-game and overall banner-required warnings disappear after a successful upload |  |
| [`OC-EDT-001`](OC-EDT-001.cmd) | OtherCategoryTest | Edit loads every saved field correctly, including all 12 language names |  |
| [`OC-EDT-002`](OC-EDT-002.cmd) | OtherCategoryTest | Changing only the English name leaves the other 11 languages untouched |  |
| [`OC-EDT-003`](OC-EDT-003.cmd) | OtherCategoryTest | Uploading a new icon during Edit replaces the previous one |  |
| [`OC-EDT-004`](OC-EDT-004.cmd) | OtherCategoryTest | Changing Status and saving updates the category's visibility state |  |
| [`OC-EDT-005`](OC-EDT-005.cmd) | OtherCategoryTest | Lowering a category's Order moves it earlier in the list |  |
| [`OC-EDT-006`](OC-EDT-006.cmd) | OtherCategoryTest | Opening Edit and saving immediately without changes |  |
| [`OC-EDT-007`](OC-EDT-007.cmd) | OtherCategoryTest | Changing fields and clicking Cancel does not save them |  |
| [`OC-EDT-008`](OC-EDT-008.cmd) | OtherCategoryTest | Confirming whether Edit's save button reads "Save" or "Save & manage games", and where it navigates |  |
| [`OC-I18N-001`](OC-I18N-001.cmd) | OtherCategoryTest | All 12 language tabs are present, labeled, and selectable |  |
| [`OC-I18N-002`](OC-I18N-002.cmd) | OtherCategoryTest | Switching the admin UI's display language does not change which category-name tab is active |  |
| [`OC-I18N-003`](OC-I18N-003.cmd) | OtherCategoryTest | Client UI shows the category name matching the end user's chosen language | creates-data |
| [`OC-I18N-004`](OC-I18N-004.cmd) | OtherCategoryTest | Client behavior for a language whose name field was left blank on admin side | creates-data |
| [`OC-I18N-005`](OC-I18N-005.cmd) | OtherCategoryTest | Buttons, hints, and warnings on the Other Category screens render correctly in both admin UI languages |  |
| [`OC-CLI-001`](OC-CLI-001.cmd) | OtherCategoryTest | Inactive categories are excluded from the client-facing list | creates-data |
| [`OC-CLI-002`](OC-CLI-002.cmd) | OtherCategoryTest | An Inactive category does not appear anywhere on the client UI, including deep links |  |
| [`OC-CLI-003`](OC-CLI-003.cmd) | OtherCategoryTest | Client-facing category order matches the admin-configured Order |  |
| [`OC-CLI-004`](OC-CLI-004.cmd) | OtherCategoryTest | Client-side behavior for an Active category that has no games |  |
| [`OC-CLI-005`](OC-CLI-005.cmd) | OtherCategoryTest | The uploaded category icon displays correctly on the client UI |  |
| [`OC-CLI-006`](OC-CLI-006.cmd) | OtherCategoryTest | A game's banner on the client UI matches the one uploaded for that specific category assignment |  |
| [`OC-CLI-007`](OC-CLI-007.cmd) | OtherCategoryTest | The same game shows a different, correct banner in each category it belongs to |  |
| [`OC-CLI-008`](OC-CLI-008.cmd) | OtherCategoryTest | Deleting a category on admin side removes it from the client platform without delay |  |
| [`OC-CLI-009`](OC-CLI-009.cmd) | OtherCategoryTest | A client user viewing in a given language always sees that language's saved category name |  |
| [`OC-CLI-010`](OC-CLI-010.cmd) | OtherCategoryTest | Client-side behavior for a selected game whose banner was never uploaded |  |
| [`OC-REG-001`](OC-REG-001.cmd) | OtherCategoryTest | Deleting a category does not corrupt or remove the underlying global game catalog |  |
| [`OC-REG-002`](OC-REG-002.cmd) | OtherCategoryTest | A multi-category game loses only the deleted category's assignment, keeping the rest |  |
| [`OC-REG-003`](OC-REG-003.cmd) | OtherCategoryTest | Changing one category's Order or Status does not alter any other category's saved values |  |
| [`OC-REG-004`](OC-REG-004.cmd) | OtherCategoryTest | Two admin sessions editing the same category at nearly the same time |  |
| [`OC-DEL-001`](OC-DEL-001.cmd) | OtherCategoryTest | The delete confirmation names the right category and its true game-assignment count |  |
| [`OC-DEL-002`](OC-DEL-002.cmd) | OtherCategoryTest | Clicking Delete category in the confirmation removes it from the list |  |
| [`OC-DEL-003`](OC-DEL-003.cmd) | OtherCategoryTest | Clicking Cancel in the confirmation dialog does not delete the category |  |
| [`OC-DEL-004`](OC-DEL-004.cmd) | OtherCategoryTest | A game that was assigned to a deleted category still exists and can be assigned elsewhere |  |
| [`OC-DEL-005`](OC-DEL-005.cmd) | OtherCategoryTest | Checking whether banners uploaded for the deleted category's game assignments are removed or retained |  |
| [`OC-DEL-006`](OC-DEL-006.cmd) | OtherCategoryTest | Deleting a category showing "0 games" in the list |  |
| [`OC-CLI-011`](OC-CLI-011.cmd) | UiOtherCategoryClientHK | The new UI home page lists every category the client is served, in order, each with icon, name and More Games |  |
| [`OC-CLI-012`](OC-CLI-012.cmd) | UiOtherCategoryClientHK | Every card in a section draws the banner served for that game's assignment, one card per served game |  |
| [`OC-CLI-013`](OC-CLI-013.cmd) | UiOtherCategoryClientHK | A row with more cards than fit scrolls by its arrows until the last card is in view; a row that fits has none |  |
| [`OC-CLI-014`](OC-CLI-014.cmd) | UiOtherCategoryClientHK | More Games opens the category's own page, headed with its name and listing every served game |  |
| [`OC-CLI-015`](OC-CLI-015.cmd) | UiOtherCategoryClientHK | A game under maintenance is distinguished on its card (confirm whether) |  |
| [`OC-CLI-016`](OC-CLI-016.cmd) | UiOtherCategoryClientHK | A visitor who opens a game card is asked to log in, and no game opens |  |
| [`OC-CLI-017`](OC-CLI-017.cmd) | UiOtherCategoryClientHK | Under Traditional Chinese each section is headed with the category's 繁體 name |  |
| [`OC-CLI-018`](OC-CLI-018.cmd) | UiOtherCategoryClientHK | A category page for an id the client does not serve shows "No Games" rather than an error |  |
| [`OC-CLI-019`](OC-CLI-019.cmd) | UiOtherCategoryClientHK | A signed-in member who opens a game card is not asked to log in again (the game launches) |  |
| [`OC-CLI-030`](OC-CLI-030.cmd) | UiOtherCategoryClientHK | A signed-in member can open every game listed in every section on the home page |  |
| [`OC-CLI-031`](OC-CLI-031.cmd) | UiOtherCategoryClientHK | A signed-in member can open every game on every category's own page |  |
| [`OC-CLI-020`](OC-CLI-020.cmd) | UiOtherCategoryClientHK | The sections and category page render and work on a PC (Windows, Chrome, 1920x1080) |  |
| [`OC-CLI-021`](OC-CLI-021.cmd) | UiOtherCategoryClientHK | The sections and category page render and work on a Mac (Safari/WebKit, 1440x900) |  |
| [`OC-CLI-022`](OC-CLI-022.cmd) | UiOtherCategoryClientHK | The sections and category page render and work on an iPad (Safari/WebKit, 820x1180, touch) |  |
| [`OC-CLI-023`](OC-CLI-023.cmd) | UiOtherCategoryClientHK | The sections and category page render and work on an iPhone (Safari/WebKit, 390x844, touch) |  |
| [`OC-CLI-024`](OC-CLI-024.cmd) | UiOtherCategoryClientHK | The sections and category page render and work on an Android phone (Chrome, 412x915, touch) |  |
| [`OC-CLI-025`](OC-CLI-025.cmd) | UiOtherCategoryClientHK | A signed-in member sees the sections and opens a game on a PC (Windows, Chrome, 1920x1080) |  |
| [`OC-CLI-026`](OC-CLI-026.cmd) | UiOtherCategoryClientHK | A signed-in member sees the sections and opens a game on a Mac (Safari/WebKit, 1440x900) |  |
| [`OC-CLI-027`](OC-CLI-027.cmd) | UiOtherCategoryClientHK | A signed-in member sees the sections and opens a game on an iPad (Safari/WebKit, 820x1180, touch) |  |
| [`OC-CLI-028`](OC-CLI-028.cmd) | UiOtherCategoryClientHK | A signed-in member sees the sections and opens a game on an iPhone (Safari/WebKit, 390x844, touch) |  |
| [`OC-CLI-029`](OC-CLI-029.cmd) | UiOtherCategoryClientHK | A signed-in member sees the sections and opens a game on an Android phone (Chrome, 412x915, touch) |  |
| [`SPD-001`](SPD-001.cmd) | ClientValidDepositTest | With only System (Wallet) Promotion Active, confirming a qualifying client deposit creates and confirms its linked System Promotion record, and creates no System Live Promotion record | creates-data |
| [`SPD-002`](SPD-002.cmd) | ClientValidDepositTest | With only System Live Promotion Active, confirming a qualifying client deposit creates and confirms its linked System Live Promotion record, and creates no System Promotion record | creates-data |
| [`SPD-003`](SPD-003.cmd) | ClientValidDepositTest | Confirming a qualifying client deposit with both promotions Active cascades to both children, each credited in its own currency | creates-data |
| [`SPR-017`](SPR-017.cmd) | SystemPromotionRecordsTest | Trade > Transaction Records opens and is headed correctly |  |
| [`SPR-018`](SPR-018.cmd) | SystemPromotionRecordsTest | The results table carries the fourteen columns the back office draws, Deposit/Expenditure and Confirmer among them |  |
| [`SPR-001`](SPR-001.cmd) | SystemPromotionRecordsTest | A deposit meeting both promotions' minimum amount creates three linked records - Main Deposit, System Promotion, System Live Promotion |  |
| [`SPR-002`](SPR-002.cmd) | SystemPromotionRecordsTest | With only System (Wallet) Promotion enabled, a qualifying deposit creates two records - Main Deposit and System Promotion, no Live record |  |
| [`SPR-003`](SPR-003.cmd) | SystemPromotionRecordsTest | With only System Live Promotion enabled, a qualifying deposit creates two records - Main Deposit and System Live Promotion, no Wallet record |  |
| [`SPR-004`](SPR-004.cmd) | SystemPromotionRecordsTest | A deposit below both promotions' minimum amount creates only the Main Deposit record - no promotion record is raised |  |
| [`SPR-005`](SPR-005.cmd) | SystemPromotionRecordsTest | The System Promotion and System Live Promotion records carry distinct transaction types ("System Promotion" vs "System Live Promotion") |  |
| [`SPR-006`](SPR-006.cmd) | SystemPromotionRecordsTest | Confirming the Main Deposit record also confirms both of its linked promotion records | creates-data |
| [`SPR-007`](SPR-007.cmd) | SystemPromotionRecordsTest | Confirming a Main Deposit record that has only one linked promotion record confirms that one record, and leaves no orphan pending record | creates-data |
| [`SPR-008`](SPR-008.cmd) | SystemPromotionRecordsTest | Rejecting the Main Deposit record auto-rejects both of its linked promotion records | creates-data |
| [`SPR-009`](SPR-009.cmd) | SystemPromotionRecordsTest | Rejecting the System Promotion (Wallet) record on its own does not affect the Main Deposit record, which stays pending | creates-data |
| [`SPR-010`](SPR-010.cmd) | SystemPromotionRecordsTest | Rejecting the System Promotion (Wallet) record on its own does not affect the sibling System Live Promotion record | creates-data |
| [`SPR-011`](SPR-011.cmd) | SystemPromotionRecordsTest | Rejecting the System Live Promotion record on its own does not affect the Main Deposit record, which stays pending | creates-data |
| [`SPR-012`](SPR-012.cmd) | SystemPromotionRecordsTest | Rejecting the System Live Promotion record on its own does not affect the sibling System Promotion (Wallet) record | creates-data |
| [`SPR-013`](SPR-013.cmd) | SystemPromotionRecordsTest | Confirming the Main Deposit record credits the member's main wallet by the deposit amount | creates-data |
| [`SPR-014`](SPR-014.cmd) | SystemPromotionRecordsTest | Confirming the System Promotion record credits the promotion amount in its configured target (offer) currency | creates-data |
| [`SPR-015`](SPR-015.cmd) | SystemPromotionRecordsTest | Confirming the System Live Promotion record credits its amount in the deposit's own requested currency, never a separately configured one | creates-data |
| [`SPR-016`](SPR-016.cmd) | SystemPromotionRecordsTest | The target (offer) currency chosen at setup is stored on the System Promotion record only - the System Live Promotion record carries no target-currency field and always shows the deposit's own currency |  |
| [`SPS-001`](SPS-001.cmd) | SystemPromotionSetupTest | Discount > System Promotion Setup opens and is headed correctly |  |
| [`SPS-002`](SPS-002.cmd) | SystemPromotionSetupTest | The three settings sections are drawn in the order GD-111 asks for - Overall, then Wallet left of Live on one row |  |
| [`SPS-003`](SPS-003.cmd) | SystemPromotionSetupTest | Agent group and Member group stay at the bottom of the form, below the three settings sections |  |
| [`SPS-004`](SPS-004.cmd) | SystemPromotionSetupTest | Live Promotion carries no currency field - it always uses the requested deposit currency |  |
| [`SPS-005`](SPS-005.cmd) | SystemPromotionSetupTest | The Time Range field and its Clear control are offered under Overall settings |  |
| [`SPS-006`](SPS-006.cmd) | SystemPromotionSetupTest | Agent group and Member group each offer Select all and Optional, with Option and Selected lists |  |
| [`SPS-007`](SPS-007.cmd) | SystemPromotionSetupTest | The form's labels follow the selected language (Traditional Chinese) |  |
| [`SPS-008`](SPS-008.cmd) | SystemPromotionSetupTest | The form's labels follow the selected language (Thai) |  |
| [`SPS-009`](SPS-009.cmd) | SystemPromotionSetupTest | The Type list offers at least one choice, and choosing one is reflected on the form | creates-data |
| [`SPS-010`](SPS-010.cmd) | SystemPromotionSetupTest | Saving the Wallet Promotion percentage and maximum discount amount is read back after a reload | creates-data |
| [`SPS-011`](SPS-011.cmd) | SystemPromotionSetupTest | Saving the Live Promotion percentage and maximum discount amount is read back after a reload | creates-data |
| [`SPS-012`](SPS-012.cmd) | SystemPromotionSetupTest | Saving a blank Wallet Promotion percentage is refused | creates-data |
| [`SPS-013`](SPS-013.cmd) | SystemPromotionSetupTest | A Wallet Promotion percentage outside 0-100 is accepted, as the system currently implements it | creates-data |
| [`SPS-014`](SPS-014.cmd) | SystemPromotionSetupTest | Setting both Agent group and Member group to Select all is accepted and holds after a reload | creates-data |
| [`SPS-015`](SPS-015.cmd) | SystemPromotionSetupTest | Targeting one Agent group and one Member group on Optional is accepted and holds after a reload | creates-data |
| [`SPS-016`](SPS-016.cmd) | SystemPromotionSetupTest | Saving Status Active with a Time Range wholly in the past is refused | creates-data, known-defect |
| [`RBMP-056`](RBMP-056.cmd) | DailyRebateTest | Daily tab lists only Daily programs with correct summary data |  |
| [`RBMP-057`](RBMP-057.cmd) | DailyRebateTest | Search/filter the program list by name or target |  |
| [`RBMP-058`](RBMP-058.cmd) | DailyRebateTest | Filter by Status (All / Active / Inactive) |  |
| [`RBMP-059`](RBMP-059.cmd) | DailyRebateTest | Modify action opens the correct program pre-filled |  |
| [`RBMP-060`](RBMP-060.cmd) | DailyRebateTest | Delete requires confirmation and removes the program | creates-data |
| [`RBMP-061`](RBMP-061.cmd) | DailyRebateTest | Pagination on the program list | creates-data |
| [`RBMP-062`](RBMP-062.cmd) | DailyRebateTest | Daily global settings display current saved values |  |
| [`RBMP-063`](RBMP-063.cmd) | DailyRebateTest | Daily Release Time cannot exceed Expire Time | creates-data |
| [`RBMP-064`](RBMP-064.cmd) | DailyRebateTest | Daily Release/Expire Time counts from 00:00 | creates-data |
| [`RBMP-065`](RBMP-065.cmd) | DailyRebateTest | Create a Daily rebate program with all fields valid | creates-data |
| [`RBMP-066`](RBMP-066.cmd) | DailyRebateTest | Return/Back navigation from Create returns to the correct tab |  |
| [`RBMP-067`](RBMP-067.cmd) | DailyRebateTest | A program's rebate Type is fixed and cannot be changed on the Create screen |  |
| [`RBMP-068`](RBMP-068.cmd) | DailyRebateTest | Attempt to create a program with an empty Program Name |  |
| [`RBMP-069`](RBMP-069.cmd) | DailyRebateTest | Attempt to create a program with no Agent group or no User Group selected |  |
| [`RBMP-070`](RBMP-070.cmd) | DailyRebateTest | Create is blocked when the pairing duplicates another program of the SAME type |  |
| [`RBMP-071`](RBMP-071.cmd) | DailyRebateTest | Resubmitting after correcting a flagged duplicate-pairing conflict succeeds | creates-data |
| [`RBMP-072`](RBMP-072.cmd) | DailyRebateTest | New program defaults to Inactive regardless of entered data | creates-data |
| [`RBMP-073`](RBMP-073.cmd) | DailyRebateTest | Explicitly setting Status = Active on create is honored | creates-data |
| [`RBMP-074`](RBMP-074.cmd) | DailyRebateTest | Cancel discards a partially-completed Create form |  |
| [`RBMP-075`](RBMP-075.cmd) | DailyRebateTest | Create a program using a Program Name identical to an existing program | creates-data |
| [`RBMP-076`](RBMP-076.cmd) | DailyRebateTest | Program Name field boundary - very long text and special characters |  |
| [`RBMP-077`](RBMP-077.cmd) | DailyRebateTest | Modify and save a change to Program Name | creates-data |
| [`RBMP-078`](RBMP-078.cmd) | DailyRebateTest | Toggle a program's Status between Active and Inactive | creates-data |
| [`RBMP-079`](RBMP-079.cmd) | DailyRebateTest | Modify the pairing to one already used by another program of the same type |  |
| [`RBMP-080`](RBMP-080.cmd) | DailyRebateTest | Modify unrelated fields while leaving the program's own pairing unchanged | creates-data |
| [`RBMP-081`](RBMP-081.cmd) | DailyRebateTest | Change a game category's rebate rate | creates-data |
| [`RBMP-082`](RBMP-082.cmd) | DailyRebateTest | Set a game category's rate to 0% | creates-data |
| [`RBMP-083`](RBMP-083.cmd) | DailyRebateTest | Attempt to change a program's rebate Type during Modify |  |
| [`RBMP-084`](RBMP-084.cmd) | DailyRebateTest | Open Modify and click Update with no changes made |  |
| [`RBMP-085`](RBMP-085.cmd) | DailyRebateTest | Delete a program that is not currently Active | creates-data |
| [`RBMP-086`](RBMP-086.cmd) | DailyRebateTest | Delete a currently Active program | creates-data, known-defect |
| [`RBMP-087`](RBMP-087.cmd) | DailyRebateTest | A deleted program's pairing becomes available for reuse within the same type | creates-data |
| [`RBMP-088`](RBMP-088.cmd) | DailyRebateTest | Delete confirmation can be cancelled |  |
| [`RBMP-089`](RBMP-089.cmd) | DailyRebateTest | "Select all" (>>) moves every available Agent into Selected |  |
| [`RBMP-090`](RBMP-090.cmd) | DailyRebateTest | Move a single Agent using -> and remove using <- |  |
| [`RBMP-091`](RBMP-091.cmd) | DailyRebateTest | Clear all Selected Agents using << |  |
| [`RBMP-092`](RBMP-092.cmd) | DailyRebateTest | Same transfer-selection behavior verified for the User Group control |  |
| [`RBMP-093`](RBMP-093.cmd) | DailyRebateTest | Duplicate-pairing validation triggers on Submit for Create, and on Update for Modify | creates-data |
| [`RBMP-094`](RBMP-094.cmd) | DailyRebateTest | Multiple Agents x multiple User Groups generates a full cross-product, all checked on Submit | creates-data |
| [`RBMP-095`](RBMP-095.cmd) | DailyRebateTest | Editing without changing a program's own pairing does not self-conflict |  |
| [`RBMP-096`](RBMP-096.cmd) | DailyRebateTest | Newly created Agent or User Group appears as selectable without a workaround |  |
| [`RBMP-097`](RBMP-097.cmd) | DailyRebateTest | "Default" User Group option behavior |  |
| [`RBMP-098`](RBMP-098.cmd) | DailyRebateTest | All game categories are pre-populated automatically on Create |  |
| [`RBMP-099`](RBMP-099.cmd) | DailyRebateTest | Setting a category's rate to 0% is accepted and means no rebate for that category | creates-data |
| [`RBMP-100`](RBMP-100.cmd) | DailyRebateTest | Rebate rate field rejects negative values | known-defect |
| [`RBMP-101`](RBMP-101.cmd) | DailyRebateTest | Rebate rate field rejects non-numeric input | known-defect |
| [`RBMP-102`](RBMP-102.cmd) | DailyRebateTest | Rebate rates are NOT required to sum to 100% across categories |  |
| [`RBMP-103`](RBMP-103.cmd) | DailyRebateTest | Maximum rebate rate boundary per category |  |
| [`RBMP-104`](RBMP-104.cmd) | DailyRebateTest | Decimal precision on rebate rate |  |
| [`RBMP-105`](RBMP-105.cmd) | DailyRebateTest | Editing a category's rate updates future settlement without altering past settled amounts | creates-data |
| [`RBMP-106`](RBMP-106.cmd) | DailyRebateTest | Daily rebate settles on the scheduler's cycle, not immediately |  |
| [`RBMP-107`](RBMP-107.cmd) | DailyRebateTest | Admin cannot manually trigger or edit a Daily settlement run |  |
| [`RBMP-108`](RBMP-108.cmd) | DailyRebateTest | Rebate credited exactly at the Daily Expire Time boundary |  |
| [`RBMP-109`](RBMP-109.cmd) | DailyRebateTest | An Inactive program does not generate settlements even with a configured rate | creates-data |
| [`RBMP-110`](RBMP-110.cmd) | DailyRebateTest | No double-settlement on a retried or reprocessed batch |  |
| [`RBMP-111`](RBMP-111.cmd) | DailyRebateTest | Settlement report/log reflects which program and rate produced each credited rebate | known-defect |
| [`RBMP-001`](RBMP-001.cmd) | RealTimeRebateTest | Real-time tab lists only Real-time programs with correct summary data |  |
| [`RBMP-002`](RBMP-002.cmd) | RealTimeRebateTest | Search/filter the program list by name or target |  |
| [`RBMP-003`](RBMP-003.cmd) | RealTimeRebateTest | Filter by Status (All / Active / Inactive) |  |
| [`RBMP-004`](RBMP-004.cmd) | RealTimeRebateTest | Modify action opens the correct program pre-filled |  |
| [`RBMP-005`](RBMP-005.cmd) | RealTimeRebateTest | Delete requires confirmation and removes the program | creates-data |
| [`RBMP-006`](RBMP-006.cmd) | RealTimeRebateTest | Pagination on the program list | creates-data |
| [`RBMP-007`](RBMP-007.cmd) | RealTimeRebateTest | Real-time global settings display current saved values |  |
| [`RBMP-008`](RBMP-008.cmd) | RealTimeRebateTest | Save valid changes to Real-time global settings | creates-data |
| [`RBMP-009`](RBMP-009.cmd) | RealTimeRebateTest | Real-time Min Claim rejects negative or non-numeric input | creates-data, known-defect |
| [`RBMP-010`](RBMP-010.cmd) | RealTimeRebateTest | Toggling Real-time global Status to Inactive affects all Real-time programs | creates-data |
| [`RBMP-011`](RBMP-011.cmd) | RealTimeRebateTest | Create a Real-time rebate program with all fields valid | creates-data |
| [`RBMP-012`](RBMP-012.cmd) | RealTimeRebateTest | A program's rebate Type is fixed and cannot be changed on the Create screen |  |
| [`RBMP-013`](RBMP-013.cmd) | RealTimeRebateTest | Attempt to create a program with an empty Program Name |  |
| [`RBMP-014`](RBMP-014.cmd) | RealTimeRebateTest | Attempt to create a program with no Agent group or no User Group selected |  |
| [`RBMP-015`](RBMP-015.cmd) | RealTimeRebateTest | Create is blocked when the pairing duplicates another program of the SAME type |  |
| [`RBMP-016`](RBMP-016.cmd) | RealTimeRebateTest | Resubmitting after correcting a flagged duplicate-pairing conflict succeeds | creates-data |
| [`RBMP-017`](RBMP-017.cmd) | RealTimeRebateTest | New program defaults to Inactive regardless of entered data | creates-data |
| [`RBMP-018`](RBMP-018.cmd) | RealTimeRebateTest | Explicitly setting Status = Active on create is honored | creates-data |
| [`RBMP-019`](RBMP-019.cmd) | RealTimeRebateTest | Cancel discards a partially-completed Create form |  |
| [`RBMP-020`](RBMP-020.cmd) | RealTimeRebateTest | Create a program using a Program Name identical to an existing program |  |
| [`RBMP-021`](RBMP-021.cmd) | RealTimeRebateTest | Program Name field boundary - very long text and special characters |  |
| [`RBMP-022`](RBMP-022.cmd) | RealTimeRebateTest | Modify and save a change to Program Name | creates-data |
| [`RBMP-023`](RBMP-023.cmd) | RealTimeRebateTest | Toggle a program's Status between Active and Inactive | creates-data |
| [`RBMP-024`](RBMP-024.cmd) | RealTimeRebateTest | Modify the pairing to one already used by another program of the same type | creates-data |
| [`RBMP-025`](RBMP-025.cmd) | RealTimeRebateTest | Modify unrelated fields while leaving the program's own pairing unchanged | creates-data |
| [`RBMP-026`](RBMP-026.cmd) | RealTimeRebateTest | Change a game category's rebate rate | creates-data |
| [`RBMP-027`](RBMP-027.cmd) | RealTimeRebateTest | Set a game category's rate to 0% | creates-data |
| [`RBMP-028`](RBMP-028.cmd) | RealTimeRebateTest | Attempt to change a program's rebate Type during Modify |  |
| [`RBMP-029`](RBMP-029.cmd) | RealTimeRebateTest | Open Modify and click Update with no changes made |  |
| [`RBMP-030`](RBMP-030.cmd) | RealTimeRebateTest | Delete a program that is not currently Active | creates-data |
| [`RBMP-031`](RBMP-031.cmd) | RealTimeRebateTest | Delete a currently Active program | creates-data, known-defect |
| [`RBMP-032`](RBMP-032.cmd) | RealTimeRebateTest | A deleted program's pairing becomes available for reuse within the same type | creates-data |
| [`RBMP-033`](RBMP-033.cmd) | RealTimeRebateTest | Delete confirmation can be cancelled |  |
| [`RBMP-034`](RBMP-034.cmd) | RealTimeRebateTest | "Select all" (>>) moves every available Agent into Selected and selects the Member group's Default value |  |
| [`RBMP-035`](RBMP-035.cmd) | RealTimeRebateTest | Move a single Agent using -> and remove using <- |  |
| [`RBMP-036`](RBMP-036.cmd) | RealTimeRebateTest | Clear all Selected Agents using << |  |
| [`RBMP-037`](RBMP-037.cmd) | RealTimeRebateTest | Same transfer-selection behavior verified for the User Group control |  |
| [`RBMP-038`](RBMP-038.cmd) | RealTimeRebateTest | Duplicate-pairing validation triggers on Submit for Create, and on Update for Modify | known-defect |
| [`RBMP-039`](RBMP-039.cmd) | RealTimeRebateTest | Multiple Agents x multiple User Groups generates a full cross-product, all checked on Submit | creates-data |
| [`RBMP-040`](RBMP-040.cmd) | RealTimeRebateTest | Editing without changing a program's own pairing does not self-conflict |  |
| [`RBMP-041`](RBMP-041.cmd) | RealTimeRebateTest | Newly created Agent or User Group appears as selectable without a workaround |  |
| [`RBMP-042`](RBMP-042.cmd) | RealTimeRebateTest | "Default" User Group option behavior |  |
| [`RBMP-043`](RBMP-043.cmd) | RealTimeRebateTest | All game categories are pre-populated automatically on Create |  |
| [`RBMP-044`](RBMP-044.cmd) | RealTimeRebateTest | Setting a category's rate to 0% is accepted and means no rebate for that category | creates-data |
| [`RBMP-045`](RBMP-045.cmd) | RealTimeRebateTest | Rebate rate field rejects negative values | known-defect |
| [`RBMP-046`](RBMP-046.cmd) | RealTimeRebateTest | Rebate rate field rejects non-numeric input | known-defect |
| [`RBMP-047`](RBMP-047.cmd) | RealTimeRebateTest | Rebate rates are NOT required to sum to 100% across categories |  |
| [`RBMP-048`](RBMP-048.cmd) | RealTimeRebateTest | Maximum rebate rate boundary per category |  |
| [`RBMP-049`](RBMP-049.cmd) | RealTimeRebateTest | Decimal precision on rebate rate |  |
| [`RBMP-050`](RBMP-050.cmd) | RealTimeRebateTest | Editing a category's rate updates future settlement without altering past settled amounts | creates-data |
| [`RBMP-051`](RBMP-051.cmd) | RealTimeRebateTest | Real-time rebate settles immediately after each round's result |  |
| [`RBMP-052`](RBMP-052.cmd) | RealTimeRebateTest | Rebate below the Real-time Min Claim threshold |  |
| [`RBMP-053`](RBMP-053.cmd) | RealTimeRebateTest | An Inactive program does not generate settlements even with a configured rate |  |
| [`RBMP-054`](RBMP-054.cmd) | RealTimeRebateTest | No double-settlement on a retried or reprocessed batch |  |
| [`RBMP-055`](RBMP-055.cmd) | RealTimeRebateTest | Settlement report/log reflects which program and rate produced each credited rebate | known-defect |
| [`RBMP-112`](RBMP-112.cmd) | RealTimeRebateTest | Switching tabs and each tab's own search/filter state |  |
| [`RBMP-113`](RBMP-113.cmd) | RealTimeRebateTest | Global settings are saved independently per rebate type | creates-data |
| [`RBMP-114`](RBMP-114.cmd) | RealTimeRebateTest | The SAME pairing used by a Real-time program is allowed on a Daily program | creates-data |
| [`RBMP-034-1 (ONE AGENT GROUP WITH THE MEMBER GROUP'S DEFAULT`](RBMP-034-1__ONE_AGENT_GROUP_WITH_THE_MEMBER_GROUP_S_DEFAULT.cmd) | RealTimeRebateTest | "Select all" (>>) moves every available Agent into Selected and selects the Member group's Default value |  |
| [`RBMP-115`](RBMP-115.cmd) | RebateAccessControlTest | Super Admin has full access to Rebate program management |  |
| [`RBMP-116`](RBMP-116.cmd) | RebateAccessControlTest | Admin role's access level to Rebate program management |  |
| [`RBMP-117`](RBMP-117.cmd) | RebateAccessControlTest | Agent role cannot access Rebate program admin setup |  |
| [`RBMP-118`](RBMP-118.cmd) | RebateAccessControlTest | Ordinary user cannot access Rebate program admin setup |  |
| [`RBMP-119`](RBMP-119.cmd) | RebateAccessControlTest | Direct deep-link to Create/Modify URLs without the proper role |  |
| [`RBMP-120`](RBMP-120.cmd) | RebateLocalizationTest | All static labels render correctly in English |  |
| [`RBMP-121`](RBMP-121.cmd) | RebateLocalizationTest | All static labels render correctly in Chinese |  |
| [`RBMP-123`](RBMP-123.cmd) | RebateLocalizationTest | Currency display is consistent across screens and languages |  |
| [`RBMP-122`](RBMP-122.cmd) | RebateLocalizationTest | The duplicate-pairing error message is translated |  |
| [`RBMP-124`](RBMP-124.cmd) | RebateMigrationTest | Existing single-program rebate data migrates into the new multi-program list |  |
| [`RBMP-125`](RBMP-125.cmd) | RebateMigrationTest | Member-facing rebate crediting is unaffected for a migrated program |  |
| [`RBMP-126`](RBMP-126.cmd) | RebateMigrationTest | Historical rebate reports remain accurate after the multi-program change |  |
| [`RBMP-127`](RBMP-127.cmd) | RebateMigrationTest | Pre-existing Agent/User Group assignments are not duplicated or corrupted by the migration |  |
| [`RBMP-136`](RBMP-136.cmd) | RebateReportTest | Filter the rebate report by date time |  |
| [`RBMP-137`](RBMP-137.cmd) | RebateReportTest | Filter the rebate report by Product (game name) |  |
| [`RBMP-138`](RBMP-138.cmd) | RebateReportTest | Type filter defaults to All and offers both rebate types |  |
| [`RBMP-145`](RBMP-145.cmd) | RebateReportTest | Filter the rebate report by Type = All |  |
| [`RBMP-146`](RBMP-146.cmd) | RebateReportTest | Filter the rebate report by Type = Daily rebate |  |
| [`RBMP-147`](RBMP-147.cmd) | RebateReportTest | Filter the rebate report by Type = Real-time rebate |  |
| [`RBMP-139`](RBMP-139.cmd) | RebateReportTest | Filter the rebate report by Status |  |
| [`RBMP-140`](RBMP-140.cmd) | RebateReportTest | Filter the rebate report by Agent account |  |
| [`RBMP-141`](RBMP-141.cmd) | RebateReportTest | An invalid Agent account returns No data |  |
| [`RBMP-142`](RBMP-142.cmd) | RebateReportTest | Filter the rebate report by Username |  |
| [`RBMP-143`](RBMP-143.cmd) | RebateReportTest | An invalid Username returns No data |  |
| [`RBMP-144`](RBMP-144.cmd) | RebateReportTest | Filter the rebate report by Member group |  |
| [`RBMP-152`](RBMP-152.cmd) | RebateReportTest | Reset all returns every filter to its default |  |
| [`RBMP-153`](RBMP-153.cmd) | RebateReportTest | The report relabels its contents in the selected language |  |
| [`RBMP-154`](RBMP-154.cmd) | RebateReportTest | The rebate record table shows the agreed column headings |  |
| [`RBMP-134`](RBMP-134.cmd) | RebateReportTest | The report lists every member who earned a rebate, with totals |  |
| [`RBMP-135`](RBMP-135.cmd) | RebateReportTest | Clicking a member shows that member's own rebate records |  |
| [`RBMP-148`](RBMP-148.cmd) | RebateReportTest | Rebate value display when the first bet reaches no experience group |  |
| [`RBMP-149`](RBMP-149.cmd) | RebateReportTest | Rebate value display when the first bet reaches level 1 |  |
| [`RBMP-150`](RBMP-150.cmd) | RebateReportTest | Rebate value display when experience reaches level 2 |  |
| [`RBMP-151`](RBMP-151.cmd) | RebateReportTest | Rebate value display for a Real-time rebate record |  |
| [`RBMP-131`](RBMP-131.cmd) | RebateSetUpTest | The rebate set up list follows the selected language (English / Chinese) |  |
| [`RBMP-133`](RBMP-133.cmd) | RebateSetUpTest | The rebate set up list follows the selected language (Thai) |  |
| [`RBMP-128`](RBMP-128.cmd) | RebateSetUpTest | Modify the Daily release time | creates-data |
| [`RBMP-129`](RBMP-129.cmd) | RebateSetUpTest | Modify the Daily expire time | creates-data |
| [`RBMP-132`](RBMP-132.cmd) | RebateSetUpTest | Min. Claim accepts an amount with two decimal places | creates-data |
| [`RBMP-130`](RBMP-130.cmd) | RebateSetUpTest | Modify the Rebate status | creates-data |
| [`NOT-CRT-901`](NOT-CRT-901.cmd) | NotificationSeedData | Seed 50 Inactive notifications in one shot, kept for the paging rows | creates-data |
| [`NOT-DEL-901`](NOT-DEL-901.cmd) | NotificationSeedData | Delete every seeded notification | creates-data |
| [`NOT-ACC-001`](NOT-ACC-001.cmd) | NotificationTest | Super Admin can Create, Edit, Delete notifications and view Record History | creates-data |
| [`NOT-ACC-002`](NOT-ACC-002.cmd) | NotificationTest | Admin has full parity with Super Admin for Notification management | creates-data |
| [`NOT-ACC-003`](NOT-ACC-003.cmd) | NotificationTest | Agent role cannot access Notification management |  |
| [`NOT-ACC-004`](NOT-ACC-004.cmd) | NotificationTest | A client-side member account cannot access admin Notification management |  |
| [`NOT-ACC-005`](NOT-ACC-005.cmd) | NotificationTest | Direct deep-link to Add/Edit Notification URLs without the proper role |  |
| [`NOT-CRT-001`](NOT-CRT-001.cmd) | NotificationTest | Create succeeds with Title, Content, and Link filled, a valid Timer, and at least one target group | creates-data |
| [`NOT-CRT-002`](NOT-CRT-002.cmd) | NotificationTest | Leaving Title empty on the active language tab blocks Save | creates-data |
| [`NOT-CRT-003`](NOT-CRT-003.cmd) | NotificationTest | Leaving Content empty on the active language tab blocks Save | creates-data |
| [`NOT-CRT-004`](NOT-CRT-004.cmd) | NotificationTest | Leaving Link empty on the active language tab still saves - Link is optional | creates-data |
| [`NOT-CRT-005`](NOT-CRT-005.cmd) | NotificationTest | Entering a malformed URL into Link is saved as entered - Link is not validated | creates-data |
| [`NOT-CRT-006`](NOT-CRT-006.cmd) | NotificationTest | Saving with only a non-mandatory language filled (e.g. 日語), leaving the required English, 简体, and 繁體 tabs blank | creates-data |
| [`NOT-CRT-007`](NOT-CRT-007.cmd) | NotificationTest | Filling only English, 简体 and 繁體 is refused with "Title and Content are required for all languages" - every language needs content or Override | creates-data |
| [`NOT-CRT-008`](NOT-CRT-008.cmd) | NotificationTest | Filling only two of the three required languages (English, 简体) while leaving the third (繁體) blank still blocks Save | creates-data |
| [`NOT-CRT-009`](NOT-CRT-009.cmd) | NotificationTest | Entering content on one tab and switching away does not lose it |  |
| [`NOT-CRT-010`](NOT-CRT-010.cmd) | NotificationTest | Checking one specific language under Override copies the active tab's Title/Content to that language |  |
| [`NOT-CRT-011`](NOT-CRT-011.cmd) | NotificationTest | Checking "All languages" propagates the active tab's content to all 12 tabs in one action |  |
| [`NOT-CRT-012`](NOT-CRT-012.cmd) | NotificationTest | Editing one overridden language's content afterward does not revert or affect the others |  |
| [`NOT-CRT-013`](NOT-CRT-013.cmd) | NotificationTest | The copied content appears on the target language tab(s) right away, not only after saving |  |
| [`NOT-CRT-014`](NOT-CRT-014.cmd) | NotificationTest | Schedule Once keeps the Timer - "—" sends immediately on activation, an hour sends once at that hour |  |
| [`NOT-CRT-015`](NOT-CRT-015.cmd) | NotificationTest | Timer accepts a time-of-day value when a recurring Schedule is selected | creates-data |
| [`NOT-CRT-016`](NOT-CRT-016.cmd) | NotificationTest | Selecting Weekly adds a Week day selector alongside Timer |  |
| [`NOT-CRT-017`](NOT-CRT-017.cmd) | NotificationTest | Attempting to save a Weekly notification without selecting a Week day | creates-data |
| [`NOT-CRT-018`](NOT-CRT-018.cmd) | NotificationTest | Timer is confirmed to use hour-level granularity (e.g. 12 AM, 1 AM ... 11 PM), with no minute-level precision available |  |
| [`NOT-CRT-019`](NOT-CRT-019.cmd) | NotificationTest | Switching Schedule from Weekly to Once or Daily removes the Week day field |  |
| [`NOT-CRT-020`](NOT-CRT-020.cmd) | NotificationTest | Clicking Select all on Agent Group moves every available Agent |  |
| [`NOT-CRT-021`](NOT-CRT-021.cmd) | NotificationTest | Using the single-arrow and clear-all controls on Member Group |  |
| [`NOT-CRT-022`](NOT-CRT-022.cmd) | NotificationTest | Saving with both Selected columns empty is refused - at least one Agent Group and one Member Group are required | creates-data |
| [`NOT-CRT-023`](NOT-CRT-023.cmd) | NotificationTest | An Inactive notification is not sent, even once its schedule/Timer would otherwise fire | creates-data |
| [`NOT-CRT-024`](NOT-CRT-024.cmd) | NotificationTest | A Once notification's Status changes from Active to Inactive by itself once its single send completes | creates-data |
| [`NOT-CRT-025`](NOT-CRT-025.cmd) | NotificationTest | Daily and Weekly notifications have no end-date/occurrence-limit field and never auto-transition to Inactive | creates-data |
| [`NOT-CRT-026`](NOT-CRT-026.cmd) | NotificationTest | Clicking Cancel during Add Notification does not save anything |  |
| [`NOT-CRT-027`](NOT-CRT-027.cmd) | NotificationTest | A Once notification with a Timer is sent when the Timer is reached and then sets itself Inactive | creates-data |
| [`NOT-CRT-028`](NOT-CRT-028.cmd) | NotificationTest | Ticking Override to All languages and unticking it again leaves the other languages blank, so Save shows the languages-required validation message | creates-data |
| [`NOT-CRT-029`](NOT-CRT-029.cmd) | NotificationTest | Add Notification opens with Type System, Schedule Once, Timer — and Status Active - defaults on which a rule is sent the moment it is saved |  |
| [`NOT-CRT-030`](NOT-CRT-030.cmd) | NotificationTest | Declining the "save and send now?" confirmation of a Once rule with no Timer saves nothing and keeps the form open |  |
| [`NOT-CRT-031`](NOT-CRT-031.cmd) | NotificationTest | A Title or Content of spaces only is refused like an empty one | creates-data |
| [`NOT-CRT-033`](NOT-CRT-033.cmd) | NotificationTest | HTML and script tags typed into Title and Content save, are shown without the tags, and never run | creates-data |
| [`NOT-CRT-034`](NOT-CRT-034.cmd) | NotificationTest | Emoji and mixed-script text in Title and Content survive Save and reload unchanged | creates-data |
| [`NOT-CRT-035`](NOT-CRT-035.cmd) | NotificationTest | Ticking Override to one language and unticking it again leaves that language blank, so Save shows the languages-required validation message | creates-data |
| [`NOT-CRT-036`](NOT-CRT-036.cmd) | NotificationTest | Override ticked while the 繁體 tab is still blank - the other languages either follow the text typed afterwards or stay blank and block Save, never blank-and-saved | creates-data |
| [`NOT-CRT-037`](NOT-CRT-037.cmd) | NotificationTest | A second rule with the identical Title is either listed beside the first or refused with a message, never silently dropped | creates-data |
| [`NOT-CRT-038`](NOT-CRT-038.cmd) | NotificationTest | A Once rule whose Timer hour is already past today is not sent immediately - it stays Active for that hour tomorrow | creates-data |
| [`NOT-LST-001`](NOT-LST-001.cmd) | NotificationTest | List shows ID, Title, Type, Agent Group, Member Group, Schedule, Timer, Status, and Created At for every notification | creates-data |
| [`NOT-LST-002`](NOT-LST-002.cmd) | NotificationTest | Status filter shows only Active or only Inactive notifications as selected | creates-data |
| [`NOT-LST-003`](NOT-LST-003.cmd) | NotificationTest | The Type filter only ever shows System, since Deposit/Withdraw don't have admin-created rules under this screen |  |
| [`NOT-LST-004`](NOT-LST-004.cmd) | NotificationTest | Changing the list's language filter updates the displayed Title to that language's translation | creates-data |
| [`NOT-LST-005`](NOT-LST-005.cmd) | NotificationTest | Page-size selector controls how many notifications are listed at once |  |
| [`NOT-LST-006`](NOT-LST-006.cmd) | NotificationTest | Previous/Next controls navigate between pages of notifications |  |
| [`NOT-LST-010`](NOT-LST-010.cmd) | NotificationTest | The First control returns to the first page of notifications from a later page |  |
| [`NOT-LST-007`](NOT-LST-007.cmd) | NotificationTest | Clicking the edit icon on a row opens that exact notification pre-filled | creates-data |
| [`NOT-LST-008`](NOT-LST-008.cmd) | NotificationTest | A broadcast notification's Agent Group and Member Group columns both read "All" | creates-data |
| [`NOT-LST-009`](NOT-LST-009.cmd) | NotificationTest | The list shows the configured Week day alongside "Weekly", e.g. "Weekly (Sunday)" | creates-data |
| [`NOT-LST-011`](NOT-LST-011.cmd) | NotificationTest | The Last control jumps to the final page of notifications |  |
| [`NOT-LST-012`](NOT-LST-012.cmd) | NotificationTest | A filter that matches nothing shows an empty list with a zero count, not stale rows |  |
| [`NOT-LST-013`](NOT-LST-013.cmd) | NotificationTest | Status, Type and Language filters combine, and every row shown satisfies all three | creates-data |
| [`NOT-LST-014`](NOT-LST-014.cmd) | NotificationTest | Returning from Edit with Cancel keeps the list's Status and Language filters | creates-data |
| [`NOT-LST-015`](NOT-LST-015.cmd) | NotificationTest | Notifications are listed newest first, by ID |  |
| [`NOT-GRP-001`](NOT-GRP-001.cmd) | NotificationTest | Newly created Agent Groups elsewhere appear here without a workaround |  |
| [`NOT-GRP-002`](NOT-GRP-002.cmd) | NotificationTest | Newly created Member Groups elsewhere appear here without a workaround |  |
| [`NOT-GRP-003`](NOT-GRP-003.cmd) | NotificationTest | Confirming how a notification is configured to reach every Agent/Member Group | creates-data |
| [`NOT-GRP-004`](NOT-GRP-004.cmd) | NotificationTest | Two notifications targeting the identical Agent+Member Group pairing both save without any conflict error | creates-data |
| [`NOT-GRP-005`](NOT-GRP-005.cmd) | NotificationTest | Select all moves every available Agent Group into Selected |  |
| [`NOT-GRP-006`](NOT-GRP-006.cmd) | NotificationTest | Select all moves every available Member Group into Selected |  |
| [`NOT-GRP-007`](NOT-GRP-007.cmd) | NotificationTest | The single left-arrow moves one item back to Available | creates-data |
| [`NOT-GRP-008`](NOT-GRP-008.cmd) | NotificationTest | The double left arrow («) empties the entire Selected column at once | creates-data |
| [`NOT-GRP-009`](NOT-GRP-009.cmd) | NotificationTest | Selecting a single Agent Group alongside several Member Groups saves successfully (1-to-many) | creates-data |
| [`NOT-GRP-010`](NOT-GRP-010.cmd) | NotificationTest | Selecting several Agent Groups alongside one Member Group saves successfully (many-to-1) | creates-data |
| [`NOT-GRP-011`](NOT-GRP-011.cmd) | NotificationTest | A true many-to-many combination (multiple Agent Groups and multiple Member Groups at once) saves successfully | creates-data |
| [`NOT-GRP-012`](NOT-GRP-012.cmd) | NotificationTest | Optional chosen on Agent Group with nothing moved to Selected is refused at Save | creates-data |
| [`NOT-EDT-001`](NOT-EDT-001.cmd) | NotificationTest | Edit loads every saved field correctly, including all 12 language tabs | creates-data |
| [`NOT-EDT-002`](NOT-EDT-002.cmd) | NotificationTest | Changing only the English content leaves the other 11 languages untouched | creates-data |
| [`NOT-EDT-003`](NOT-EDT-003.cmd) | NotificationTest | Editing a one-time notification into a recurring one | creates-data |
| [`NOT-EDT-004`](NOT-EDT-004.cmd) | NotificationTest | Setting an Active recurring notification to Inactive stops it from firing | creates-data |
| [`NOT-EDT-005`](NOT-EDT-005.cmd) | NotificationTest | Changing a notification's Agent/Member Group targets does not retroactively alter already-sent Record History entries | creates-data |
| [`NOT-EDT-006`](NOT-EDT-006.cmd) | NotificationTest | Changing fields and clicking Cancel does not save them | creates-data |
| [`NOT-EDT-007`](NOT-EDT-007.cmd) | NotificationTest | Clearing the required Title on Edit is refused with the same validation as Create | creates-data |
| [`NOT-EDT-008`](NOT-EDT-008.cmd) | NotificationTest | Editing the 繁體 Title updates the list row at once, with the same ID and Created At | creates-data |
| [`NOT-EDT-009`](NOT-EDT-009.cmd) | NotificationTest | Changing Daily to Weekly on Edit shows Week day and saves it - the list reads the day and hour | creates-data |
| [`NOT-EDT-010`](NOT-EDT-010.cmd) | NotificationTest | Setting an Inactive Daily notification back to Active resumes firing at its Timer | creates-data |
| [`NOT-EDT-011`](NOT-EDT-011.cmd) | NotificationTest | Re-activating a Once rule that has already sent either sends it once more and ends Inactive again, or leaves it unsent - never sends it repeatedly | creates-data |
| [`NOT-EDT-012`](NOT-EDT-012.cmd) | NotificationTest | Switching both targets from Optional to Select all on Edit saves as All / All | creates-data |
| [`NOT-EDT-013`](NOT-EDT-013.cmd) | NotificationTest | Removing the Link on Edit saves, and the next delivery carries no link | creates-data |
| [`NOT-ACT-001`](NOT-ACT-001.cmd) | NotificationTest | The circular-arrow icon is a Send button that pushes the notification immediately | creates-data |
| [`NOT-ACT-002`](NOT-ACT-002.cmd) | NotificationTest | Clicking Send Now logs a fresh Record History entry | creates-data |
| [`NOT-ACT-003`](NOT-ACT-003.cmd) | NotificationTest | Send Now does not alter the rule's own Schedule or Timer | creates-data |
| [`NOT-ACT-004`](NOT-ACT-004.cmd) | NotificationTest | Send Now on an Inactive rule either sends and logs it or refuses with a message - never does nothing silently - and leaves the Status Inactive | creates-data |
| [`NOT-ACT-005`](NOT-ACT-005.cmd) | NotificationTest | Two Send Now clicks in a row log two separate Record History entries | creates-data |
| [`NOT-RHL-001`](NOT-RHL-001.cmd) | NotificationTest | Record History lists ID, Notification Title, Type, Agent Group, Member Group, Released At, and Status for every send | creates-data |
| [`NOT-RHL-002`](NOT-RHL-002.cmd) | NotificationTest | Status filter (All/Sent/Pending/Failed) narrows the history list |  |
| [`NOT-RHL-003`](NOT-RHL-003.cmd) | NotificationTest | From/To date filters narrow the history to that window |  |
| [`NOT-RHL-004`](NOT-RHL-004.cmd) | NotificationTest | Changing the language filter updates the displayed Notification Title | creates-data |
| [`NOT-RHL-005`](NOT-RHL-005.cmd) | NotificationTest | Over multiple days, a recurring notification logs a separate entry each time it fires | creates-data |
| [`NOT-RHL-006`](NOT-RHL-006.cmd) | NotificationTest | A one-time notification never generates a second history entry | creates-data |
| [`NOT-RHL-007`](NOT-RHL-007.cmd) | NotificationTest | Failed status is reserved for delivery-layer failures such as lost connection, not content or validation issues |  |
| [`NOT-RHL-008`](NOT-RHL-008.cmd) | NotificationTest | Record History's page size and Prev/Next pager work as the Notification tab's do |  |
| [`NOT-RHL-009`](NOT-RHL-009.cmd) | NotificationTest | A From date later than the To date shows nothing or is corrected, never a wrong window |  |
| [`NOT-RHL-010`](NOT-RHL-010.cmd) | NotificationTest | Clearing the From/To dates restores the whole history |  |
| [`NOT-RHL-011`](NOT-RHL-011.cmd) | NotificationTest | A broadcast send's Record History row reads All / All | creates-data |
| [`NOT-RHL-012`](NOT-RHL-012.cmd) | NotificationTest | Record History is listed newest first, by Released At and ID |  |
| [`NOT-RHD-001`](NOT-RHD-001.cmd) | NotificationTest | The detail view's Title/Content/Link match what was actually delivered, for every language tab | creates-data |
| [`NOT-RHD-002`](NOT-RHD-002.cmd) | NotificationTest | The detail view's Released At is identical to the value shown in the Record History list | creates-data |
| [`NOT-RHD-003`](NOT-RHD-003.cmd) | NotificationTest | Agent/Member Group tags in the detail view represent who the notification was actually sent to | creates-data |
| [`NOT-RHD-004`](NOT-RHD-004.cmd) | NotificationTest | Returning from a detail view preserves the Record History list's filter state |  |
| [`NOT-RHD-005`](NOT-RHD-005.cmd) | NotificationTest | Confirming what the detail view's Schedule and Timer fields represent for a single send instance | creates-data |
| [`NOT-RHD-006`](NOT-RHD-006.cmd) | NotificationTest | Confirming what is shown before a send has resolved |  |
| [`NOT-RHD-007`](NOT-RHD-007.cmd) | NotificationTest | The details of a send still open after its rule has been deleted | creates-data |
| [`NOT-RHD-008`](NOT-RHD-008.cmd) | NotificationTest | The details' Link is the link the member's message opens | creates-data |
| [`NOT-I18N-001`](NOT-I18N-001.cmd) | NotificationTest | All 12 language tabs are present, labeled, and selectable everywhere content is shown | creates-data |
| [`NOT-I18N-002`](NOT-I18N-002.cmd) | NotificationTest | Switching the admin UI's display language does not change which content-language tab is active |  |
| [`NOT-I18N-003`](NOT-I18N-003.cmd) | NotificationTest | A member sees a notification's Title/Content in their own app language, when each language was manually customized after Override | creates-data |
| [`NOT-I18N-004`](NOT-I18N-004.cmd) | NotificationTest | A notification created via "Override to All languages" without manual per-language edits shows the same untranslated text regardless of the viewer's app language | creates-data |
| [`NOT-I18N-005`](NOT-I18N-005.cmd) | NotificationTest | Client behavior for a language whose Title/Content was left blank on admin side | creates-data |
| [`NOT-I18N-006`](NOT-I18N-006.cmd) | NotificationTest | Buttons, hints, and field labels on the Notification screens render correctly in both admin UI languages |  |
| [`NOT-I18N-007`](NOT-I18N-007.cmd) | NotificationTest | Validation and confirmation messages are translated under the 中文 interface | creates-data |
| [`NOT-CLI-001`](NOT-CLI-001.cmd) | NotificationTest | Message Center shows every message actually targeting this member | creates-data |
| [`NOT-CLI-002`](NOT-CLI-002.cmd) | NotificationTest | Each tab shows only messages of its own type | creates-data |
| [`NOT-CLI-003`](NOT-CLI-003.cmd) | NotificationTest | Unread messages show a distinct indicator that read messages do not have | creates-data |
| [`NOT-CLI-004`](NOT-CLI-004.cmd) | NotificationTest | Opening an unread message clears its unread indicator | creates-data |
| [`NOT-CLI-005`](NOT-CLI-005.cmd) | NotificationTest | Tapping Mark All as Read updates every unread message in one action | creates-data |
| [`NOT-CLI-006`](NOT-CLI-006.cmd) | NotificationTest | Opening a System notification shows its Title and Content as plain text | creates-data |
| [`NOT-CLI-007`](NOT-CLI-007.cmd) | NotificationTest | Opening a Deposit notification shows Amount, Method, Transaction ID, Destination Wallet, and Status |  |
| [`NOT-CLI-008`](NOT-CLI-008.cmd) | NotificationTest | A message's configured Link opens correctly when tapped | creates-data |
| [`NOT-CLI-009`](NOT-CLI-009.cmd) | NotificationTest | A member outside a notification's Agent/Member Group does not see it | creates-data |
| [`NOT-CLI-010`](NOT-CLI-010.cmd) | NotificationTest | A notification targeting All reaches members regardless of their specific group | creates-data |
| [`NOT-CLI-011`](NOT-CLI-011.cmd) | NotificationTest | An Inactive notification produces no client-visible message | creates-data |
| [`NOT-CLI-012`](NOT-CLI-012.cmd) | NotificationTest | Deposit and Withdraw notifications fire on both completion and rejection outcomes |  |
| [`NOT-CLI-013`](NOT-CLI-013.cmd) | NotificationTest | Message Center reads the member in the same currency the suite set the notifications up under |  |
| [`NOT-CLI-014`](NOT-CLI-014.cmd) | NotificationTest | Messages are listed newest first, and Load more appends without repeating any | creates-data |
| [`NOT-CLI-015`](NOT-CLI-015.cmd) | NotificationTest | The header's unread indicator stays lit while any message is unread and clears once every one is read | creates-data |
| [`NOT-CLI-016`](NOT-CLI-016.cmd) | NotificationTest | A message's read state survives a refresh and a fresh sign-in | creates-data |
| [`NOT-CLI-017`](NOT-CLI-017.cmd) | NotificationTest | Switching the member to another wallet hides the notifications set up under the suite's currency | creates-data |
| [`NOT-CLI-019`](NOT-CLI-019.cmd) | NotificationTest | A delivered message keeps the Content as sent after the rule is edited | creates-data |
| [`NOT-CLI-020`](NOT-CLI-020.cmd) | NotificationTest | Mark All as Read with nothing unread is a harmless no-op | creates-data |
| [`NOT-REG-001`](NOT-REG-001.cmd) | NotificationTest | A recurring notification's actual send time stays consistent across multiple days |  |
| [`NOT-REG-002`](NOT-REG-002.cmd) | NotificationTest | No double-send occurs from a scheduler re-run or retry | creates-data |
| [`NOT-REG-003`](NOT-REG-003.cmd) | NotificationTest | The very next scheduled send uses the most recently saved Content, with no propagation delay | creates-data |
| [`NOT-REG-004`](NOT-REG-004.cmd) | NotificationTest | Deleting one of several targeted groups: the notification still fires, skipping only the deleted group | creates-data |
| [`NOT-REG-005`](NOT-REG-005.cmd) | NotificationTest | Deleting a notification's sole targeted Agent/Member Group: the schedule still executes rather than being blocked or silently cancelled | creates-data |
| [`NOT-REG-006`](NOT-REG-006.cmd) | NotificationTest | Confirming no migration or coexistence testing is required for the old customer-service-based mechanism |  |
| [`NOT-REG-007`](NOT-REG-007.cmd) | NotificationTest | Two admin sessions editing the same notification at nearly the same time |  |
| [`NOT-REG-010`](NOT-REG-010.cmd) | NotificationTest | Created At agrees with the header clock and with Hong Kong Time, the clock the schedules run on | creates-data |
| [`NOT-REG-011`](NOT-REG-011.cmd) | NotificationTest | Moving a Weekly rule's Timer to a later hour after it has already fired today sends it again at the new hour, exactly once | creates-data |
| [`NOT-DEL-001`](NOT-DEL-001.cmd) | NotificationTest | Clicking the delete icon prompts for confirmation before removal | creates-data |
| [`NOT-DEL-002`](NOT-DEL-002.cmd) | NotificationTest | Confirming deletion removes the notification from the list | creates-data |
| [`NOT-DEL-003`](NOT-DEL-003.cmd) | NotificationTest | Cancelling the confirmation leaves the notification untouched | creates-data |
| [`NOT-DEL-004`](NOT-DEL-004.cmd) | NotificationTest | Deleting a notification rule that already has sent history | creates-data |
| [`NOT-DEL-005`](NOT-DEL-005.cmd) | NotificationTest | Deleting an Active Daily rule stops its future firings | creates-data |
| [`NOT-DEL-006`](NOT-DEL-006.cmd) | NotificationTest | A message already delivered stays in the member's inbox after its rule is deleted | creates-data |
| [`SN-ADM-LST-001`](SN-ADM-LST-001.cmd) | OfferAndRankingAlertsTest | Menu navigation and 3 tabs | known-defect |
| [`SN-ADM-LST-002`](SN-ADM-LST-002.cmd) | OfferAndRankingAlertsTest | Switching between the three tabs | known-defect |
| [`SN-ADM-LST-003`](SN-ADM-LST-003.cmd) | OfferAndRankingAlertsTest | Add notification menu offers System notification and Offer | known-defect |
| [`SN-ADM-LST-004`](SN-ADM-LST-004.cmd) | OfferAndRankingAlertsTest | Add notification > System notification opens the System form | known-defect |
| [`SN-ADM-LST-005`](SN-ADM-LST-005.cmd) | OfferAndRankingAlertsTest | Add notification > Offer opens the Offers form | known-defect |
| [`SN-ADM-LST-006`](SN-ADM-LST-006.cmd) | OfferAndRankingAlertsTest | List columns (filters + Linked Promotion) | known-defect |
| [`SN-ADM-LST-007`](SN-ADM-LST-007.cmd) | OfferAndRankingAlertsTest | Type filter (All / System already real; Offers not yet) |  |
| [`SN-ADM-LST-008`](SN-ADM-LST-008.cmd) | OfferAndRankingAlertsTest | Linked Promotion column values (Offers truncated+hover, System em-dash) | known-defect |
| [`SN-ADM-LST-009`](SN-ADM-LST-009.cmd) | OfferAndRankingAlertsTest | Type and column naming ("Offers" / "Linked Promotion") | known-defect |
| [`SN-ADM-LST-010`](SN-ADM-LST-010.cmd) | OfferAndRankingAlertsTest | Ranking alerts are excluded from the Notification list |  |
| [`SN-ADM-LST-011`](SN-ADM-LST-011.cmd) | OfferAndRankingAlertsTest | Status and Language filters |  |
| [`SN-ADM-LST-012`](SN-ADM-LST-012.cmd) | OfferAndRankingAlertsTest | Schedule, Timer and Status display formats |  |
| [`SN-ADM-LST-013`](SN-ADM-LST-013.cmd) | OfferAndRankingAlertsTest | Edit Offer | known-defect |
| [`SN-ADM-LST-014`](SN-ADM-LST-014.cmd) | OfferAndRankingAlertsTest | Copy Offer | known-defect |
| [`SN-ADM-LST-015`](SN-ADM-LST-015.cmd) | OfferAndRankingAlertsTest | Delete Offer | known-defect |
| [`SN-ADM-LST-016`](SN-ADM-LST-016.cmd) | OfferAndRankingAlertsTest | Pagination with Type=Offer filter applied | known-defect |
| [`SN-ADM-RNK-001`](SN-ADM-RNK-001.cmd) | OfferAndRankingAlertsTest | Tab layout matches the design | known-defect |
| [`SN-ADM-RNK-002`](SN-ADM-RNK-002.cmd) | OfferAndRankingAlertsTest | No content fields - only On/Off switches | known-defect |
| [`SN-ADM-RNK-003`](SN-ADM-RNK-003.cmd) | OfferAndRankingAlertsTest | Save changes persists after refresh | known-defect |
| [`SN-ADM-RNK-004`](SN-ADM-RNK-004.cmd) | OfferAndRankingAlertsTest | An unsaved switch reverts on refresh | known-defect |
| [`SN-ADM-RNK-005`](SN-ADM-RNK-005.cmd) | OfferAndRankingAlertsTest | Cancel discards the switch | known-defect |
| [`SN-ADM-RNK-006`](SN-ADM-RNK-006.cmd) | OfferAndRankingAlertsTest | Leaving the tab with an unsaved change (OQ-20) | known-defect |
| [`SN-ADM-RNK-007`](SN-ADM-RNK-007.cmd) | OfferAndRankingAlertsTest | Admin Operation Log entry per change | known-defect |
| [`SN-ADM-REC-001`](SN-ADM-REC-001.cmd) | OfferAndRankingAlertsTest | List columns (Type filter + Sent/Seen) | known-defect |
| [`SN-ADM-REC-002`](SN-ADM-REC-002.cmd) | OfferAndRankingAlertsTest | Type filter offers System, Offer and the three Ranking types | known-defect |
| [`SN-ADM-REC-003`](SN-ADM-REC-003.cmd) | OfferAndRankingAlertsTest | Date range filter (From / To) |  |
| [`SN-ADM-REC-004`](SN-ADM-REC-004.cmd) | OfferAndRankingAlertsTest | Status and Language filters |  |
| [`SN-ADM-REC-005`](SN-ADM-REC-005.cmd) | OfferAndRankingAlertsTest | Ranking row values | known-defect |
| [`SN-ADM-REC-006`](SN-ADM-REC-006.cmd) | OfferAndRankingAlertsTest | Pending / Failed status values with Sent/Seen counts | known-defect |
| [`SN-ADM-REC-007`](SN-ADM-REC-007.cmd) | OfferAndRankingAlertsTest | Released At is HKT everywhere, with ranking snapshots converted from UTC+7 | known-defect |
| [`SN-ADM-REC-008`](SN-ADM-REC-008.cmd) | OfferAndRankingAlertsTest | System record detail | known-defect |
| [`SN-ADM-REC-009`](SN-ADM-REC-009.cmd) | OfferAndRankingAlertsTest | Offer record detail | known-defect |
| [`SN-ADM-REC-010`](SN-ADM-REC-010.cmd) | OfferAndRankingAlertsTest | Ranking - Membership detail | known-defect |
| [`SN-ADM-REC-011`](SN-ADM-REC-011.cmd) | OfferAndRankingAlertsTest | Ranking - Referral / Withdrawal detail | known-defect |
| [`SN-ADM-REC-012`](SN-ADM-REC-012.cmd) | OfferAndRankingAlertsTest | Admin sees full usernames and exact amounts across ranking records (OQ-29) | known-defect |
| [`SN-ADM-REC-013`](SN-ADM-REC-013.cmd) | OfferAndRankingAlertsTest | Seen is counted when a member opens the detail | known-defect |
| [`SN-ADM-REC-014`](SN-ADM-REC-014.cmd) | OfferAndRankingAlertsTest | Seen is counted only once per member | known-defect |
| [`SN-ADM-REC-015`](SN-ADM-REC-015.cmd) | OfferAndRankingAlertsTest | Viewing the inbox list alone does not count as Seen (OQ-05) | known-defect |
| [`SN-ADM-REC-016`](SN-ADM-REC-016.cmd) | OfferAndRankingAlertsTest | Seen % rounding | known-defect |
| [`SN-ADM-REC-017`](SN-ADM-REC-017.cmd) | OfferAndRankingAlertsTest | Claim This Offer count only counts completed claims (OQ-04) | known-defect |
| [`SN-ADM-REC-018`](SN-ADM-REC-018.cmd) | OfferAndRankingAlertsTest | Back to List keeps the filters |  |
| [`SN-NFR-UI-001`](SN-NFR-UI-001.cmd) | OfferAndRankingAlertsTest | BO UI vs Figma 5.1.0 §7 |  |
| [`SN-NFR-UI-002`](SN-NFR-UI-002.cmd) | OfferAndRankingAlertsTest | Client screens on the main browsers |  |
| [`SN-NFR-UI-003`](SN-NFR-UI-003.cmd) | OfferAndRankingAlertsTest | Responsive client widths |  |
| [`SN-NFR-UI-004`](SN-NFR-UI-004.cmd) | OfferAndRankingAlertsTest | Long titles do not break the BO list layout | creates-data |
| [`SN-NFR-UI-005`](SN-NFR-UI-005.cmd) | OfferAndRankingAlertsTest | Emoji rendering across operating systems |  |
| [`SN-NFR-UI-006`](SN-NFR-UI-006.cmd) | OfferAndRankingAlertsTest | Validation messages name the rule and the field | known-defect |
| [`SN-NFR-UI-007`](SN-NFR-UI-007.cmd) | OfferAndRankingAlertsTest | Offers form language labels match the System form exactly (OQ-24) | known-defect |
| [`SN-NFR-PRF-001`](SN-NFR-PRF-001.cmd) | OfferAndRankingAlertsTest | A 50,000+ member bulk Offer send completes within a minute |  |
| [`SN-NFR-PRF-002`](SN-NFR-PRF-002.cmd) | OfferAndRankingAlertsTest | An hourly Withdrawal snapshot processes within a minute |  |
| [`SN-NFR-PRF-003`](SN-NFR-PRF-003.cmd) | OfferAndRankingAlertsTest | Record History loads within 3 seconds |  |
| [`SN-NFR-PRF-004`](SN-NFR-PRF-004.cmd) | OfferAndRankingAlertsTest | Message Center loads within 2 seconds with 500+ notifications |  |
| [`SN-NFR-PRF-005`](SN-NFR-PRF-005.cmd) | OfferAndRankingAlertsTest | Banner loading on a slow network shows a placeholder |  |
| [`SN-NFR-SEC-001`](SN-NFR-SEC-001.cmd) | OfferAndRankingAlertsTest | BO permission control |  |
| [`SN-NFR-SEC-002`](SN-NFR-SEC-002.cmd) | OfferAndRankingAlertsTest | Requesting another member's notification by ID |  |
| [`SN-NFR-SEC-003`](SN-NFR-SEC-003.cmd) | OfferAndRankingAlertsTest | Server-side eligibility on an ineligible deposit |  |
| [`SN-NFR-SEC-004`](SN-NFR-SEC-004.cmd) | OfferAndRankingAlertsTest | XSS in Title/Content renders as text | creates-data |
| [`SN-NFR-SEC-005`](SN-NFR-SEC-005.cmd) | OfferAndRankingAlertsTest | A disguised banner upload is rejected server-side | known-defect |
| [`SN-NFR-SEC-006`](SN-NFR-SEC-006.cmd) | OfferAndRankingAlertsTest | Open-redirect protection after login |  |
| [`SN-NFR-SEC-007`](SN-NFR-SEC-007.cmd) | OfferAndRankingAlertsTest | Ranking alert client API masks data per Ranking Settings |  |
| [`SN-NFR-DAT-001`](SN-NFR-DAT-001.cmd) | OfferAndRankingAlertsTest | No duplicate sends if the scheduler restarts around send time |  |
| [`SN-NFR-DAT-002`](SN-NFR-DAT-002.cmd) | OfferAndRankingAlertsTest | A failed delivery is recorded, not silently lost |  |
| [`SN-NFR-DAT-003`](SN-NFR-DAT-003.cmd) | OfferAndRankingAlertsTest | Concurrent edit by two admins is handled without corruption |  |
| [`SN-NFR-DAT-004`](SN-NFR-DAT-004.cmd) | OfferAndRankingAlertsTest | Scheduling is in HKT regardless of the browser's own timezone | creates-data |
| [`SN-NFR-DAT-005`](SN-NFR-DAT-005.cmd) | OfferAndRankingAlertsTest | A schedule at 23:30 HKT on the offer period's last day is accepted (HKT vs UTC+7 boundary) | known-defect |
| [`SN-NFR-DAT-006`](SN-NFR-DAT-006.cmd) | OfferAndRankingAlertsTest | Offer create/edit/copy/delete each write an Admin Operation Log entry | known-defect |
| [`SMS-ADM-NAV-001`](SMS-ADM-NAV-001.cmd) | SmsSendingLogTest | Open SMS Sending Log from the Tool menu |  |
| [`SMS-ADM-NAV-002`](SMS-ADM-NAV-002.cmd) | SmsSendingLogTest | An admin without SMS Sending Log permission cannot access the page |  |
| [`SMS-ADM-NAV-003`](SMS-ADM-NAV-003.cmd) | SmsSendingLogTest | Breadcrumb navigation (Home Page / Tool) |  |
| [`SMS-ADM-NAV-004`](SMS-ADM-NAV-004.cmd) | SmsSendingLogTest | Session timeout on the SMS log redirects to login |  |
| [`SMS-ADM-SRC-001`](SMS-ADM-SRC-001.cmd) | SmsSendingLogTest | Default search state on page load |  |
| [`SMS-ADM-SRC-002`](SMS-ADM-SRC-002.cmd) | SmsSendingLogTest | Filter by Time limit range |  |
| [`SMS-ADM-SRC-003`](SMS-ADM-SRC-003.cmd) | SmsSendingLogTest | Time limit validation when end is earlier than start |  |
| [`SMS-ADM-SRC-004`](SMS-ADM-SRC-004.cmd) | SmsSendingLogTest | Time limit boundary seconds (00:00:00 and 23:59:59) |  |
| [`SMS-ADM-SRC-005`](SMS-ADM-SRC-005.cmd) | SmsSendingLogTest | Time limit maximum allowed range |  |
| [`SMS-ADM-SRC-006`](SMS-ADM-SRC-006.cmd) | SmsSendingLogTest | Search by full phone number |  |
| [`SMS-ADM-SRC-007`](SMS-ADM-SRC-007.cmd) | SmsSendingLogTest | Search by partial phone number |  |
| [`SMS-ADM-SRC-008`](SMS-ADM-SRC-008.cmd) | SmsSendingLogTest | Phone number field rejects invalid characters / injection payloads |  |
| [`SMS-ADM-SRC-009`](SMS-ADM-SRC-009.cmd) | SmsSendingLogTest | Search by Username |  |
| [`SMS-ADM-SRC-010`](SMS-ADM-SRC-010.cmd) | SmsSendingLogTest | Search by Username with no matching user |  |
| [`SMS-ADM-SRC-011`](SMS-ADM-SRC-011.cmd) | SmsSendingLogTest | Search by IP address |  |
| [`SMS-ADM-SRC-012`](SMS-ADM-SRC-012.cmd) | SmsSendingLogTest | IP address field rejects invalid format |  |
| [`SMS-ADM-SRC-013`](SMS-ADM-SRC-013.cmd) | SmsSendingLogTest | Search by Domain (main domain) |  |
| [`SMS-ADM-SRC-014`](SMS-ADM-SRC-014.cmd) | SmsSendingLogTest | Search by Domain (agent sub-domain) |  |
| [`SMS-ADM-SRC-015`](SMS-ADM-SRC-015.cmd) | SmsSendingLogTest | Filter by Method dropdown (All / SMS / Voice / Reverse SMS) |  |
| [`SMS-ADM-SRC-016`](SMS-ADM-SRC-016.cmd) | SmsSendingLogTest | Filter by Purpose dropdown |  |
| [`SMS-ADM-SRC-017`](SMS-ADM-SRC-017.cmd) | SmsSendingLogTest | Filter by Delivery status dropdown |  |
| [`SMS-ADM-SRC-018`](SMS-ADM-SRC-018.cmd) | SmsSendingLogTest | Filter by Verification result dropdown |  |
| [`SMS-ADM-SRC-019`](SMS-ADM-SRC-019.cmd) | SmsSendingLogTest | Combined filters use AND logic |  |
| [`SMS-ADM-SRC-020`](SMS-ADM-SRC-020.cmd) | SmsSendingLogTest | Leading/trailing spaces are trimmed |  |
| [`SMS-ADM-SRC-021`](SMS-ADM-SRC-021.cmd) | SmsSendingLogTest | Reset all returns every field to default |  |
| [`SMS-ADM-SRC-022`](SMS-ADM-SRC-022.cmd) | SmsSendingLogTest | Search is triggered by the Enter key |  |
| [`SMS-ADM-SRC-023`](SMS-ADM-SRC-023.cmd) | SmsSendingLogTest | Quick filter "Verified, not registered" |  |
| [`SMS-ADM-SRC-024`](SMS-ADM-SRC-024.cmd) | SmsSendingLogTest | Quick filter "Limit reached" |  |
| [`SMS-ADM-SRC-025`](SMS-ADM-SRC-025.cmd) | SmsSendingLogTest | Quick filter "Failed delivery" |  |
| [`SMS-ADM-SRC-026`](SMS-ADM-SRC-026.cmd) | SmsSendingLogTest | Returning to "All" restores the full list |  |
| [`SMS-ADM-SRC-027`](SMS-ADM-SRC-027.cmd) | SmsSendingLogTest | Quick filter combines with search conditions |  |
| [`SMS-ADM-SUM-001`](SMS-ADM-SUM-001.cmd) | SmsSendingLogTest | Codes sent card value and breakdown |  |
| [`SMS-ADM-SUM-002`](SMS-ADM-SUM-002.cmd) | SmsSendingLogTest | Verified card and % of codes sent |  |
| [`SMS-ADM-SUM-003`](SMS-ADM-SUM-003.cmd) | SmsSendingLogTest | Failed delivery card matches its quick filter count |  |
| [`SMS-ADM-SUM-004`](SMS-ADM-SUM-004.cmd) | SmsSendingLogTest | Limit reached card matches its quick filter count |  |
| [`SMS-ADM-SUM-005`](SMS-ADM-SUM-005.cmd) | SmsSendingLogTest | Verified, not registered card matches its quick filter count |  |
| [`SMS-ADM-SUM-006`](SMS-ADM-SUM-006.cmd) | SmsSendingLogTest | All five cards recalculate when search conditions change |  |
| [`SMS-ADM-LST-001`](SMS-ADM-LST-001.cmd) | SmsSendingLogTest | Table columns and order |  |
| [`SMS-ADM-LST-002`](SMS-ADM-LST-002.cmd) | SmsSendingLogTest | Default sort is newest first |  |
| [`SMS-ADM-LST-003`](SMS-ADM-LST-003.cmd) | SmsSendingLogTest | Row data matches its View details modal |  |
| [`SMS-ADM-LST-004`](SMS-ADM-LST-004.cmd) | SmsSendingLogTest | Method badge styles |  |
| [`SMS-ADM-LST-005`](SMS-ADM-LST-005.cmd) | SmsSendingLogTest | Delivery status badge colours |  |
| [`SMS-ADM-LST-006`](SMS-ADM-LST-006.cmd) | SmsSendingLogTest | Verification result badge colours |  |
| [`SMS-ADM-LST-007`](SMS-ADM-LST-007.cmd) | SmsSendingLogTest | Daily count display (n/3, 3/3 in red, Reverse SMS no limit) |  |
| [`SMS-ADM-LST-008`](SMS-ADM-LST-008.cmd) | SmsSendingLogTest | IP / Domain column shows IP, country code and domain tag |  |
| [`SMS-ADM-LST-009`](SMS-ADM-LST-009.cmd) | SmsSendingLogTest | Username column states (registered / not yet / verified-not-registered) |  |
| [`SMS-ADM-LST-010`](SMS-ADM-LST-010.cmd) | SmsSendingLogTest | Empty result shows the no-data state |  |
| [`SMS-ADM-LST-011`](SMS-ADM-LST-011.cmd) | SmsSendingLogTest | Refresh icon reloads the list |  |
| [`SMS-ADM-LST-012`](SMS-ADM-LST-012.cmd) | SmsSendingLogTest | Long username/domain values do not break the layout |  |
| [`SMS-ADM-LST-013`](SMS-ADM-LST-013.cmd) | SmsSendingLogTest | "Show [N] entries" selector changes page size |  |
| [`SMS-ADM-LST-014`](SMS-ADM-LST-014.cmd) | SmsSendingLogTest | Navigating pages (2, 3, Next, Previous, last page) |  |
| [`SMS-ADM-LST-015`](SMS-ADM-LST-015.cmd) | SmsSendingLogTest | Pagination keeps the active filters |  |
| [`SMS-ADM-LST-016`](SMS-ADM-LST-016.cmd) | SmsSendingLogTest | "Showing x to y of z" text is accurate |  |
| [`SMS-ADM-PRM-001`](SMS-ADM-PRM-001.cmd) | SmsSendingLogTest | Phone masked for an admin without full-number permission |  |
| [`SMS-ADM-PRM-002`](SMS-ADM-PRM-002.cmd) | SmsSendingLogTest | Full phone for an admin with full-number permission |  |
| [`SMS-ADM-PRM-003`](SMS-ADM-PRM-003.cmd) | SmsSendingLogTest | Version A - actions with Show more permission |  |
| [`SMS-ADM-PRM-004`](SMS-ADM-PRM-004.cmd) | SmsSendingLogTest | Version B - Show more hidden without permission |  |
| [`SMS-ADM-PRM-005`](SMS-ADM-PRM-005.cmd) | SmsSendingLogTest | An admin without Block/Unblock permission cannot block |  |
| [`SMS-ADM-PRM-006`](SMS-ADM-PRM-006.cmd) | SmsSendingLogTest | A permission change takes effect after refresh |  |
| [`SMS-ADM-VDT-001`](SMS-ADM-VDT-001.cmd) | SmsSendingLogTest | Open the View details modal |  |
| [`SMS-ADM-VDT-002`](SMS-ADM-VDT-002.cmd) | SmsSendingLogTest | REQUEST section content |  |
| [`SMS-ADM-VDT-003`](SMS-ADM-VDT-003.cmd) | SmsSendingLogTest | DELIVERY & VERIFICATION section content |  |
| [`SMS-ADM-VDT-004`](SMS-ADM-VDT-004.cmd) | SmsSendingLogTest | Code expiry is Requested at + 120 seconds |  |
| [`SMS-ADM-VDT-005`](SMS-ADM-VDT-005.cmd) | SmsSendingLogTest | History for this phone lists today's events in time order |  |
| [`SMS-ADM-VDT-006`](SMS-ADM-VDT-006.cmd) | SmsSendingLogTest | Domain label - main vs agent sub-domain |  |
| [`SMS-ADM-VDT-007`](SMS-ADM-VDT-007.cmd) | SmsSendingLogTest | Close View details via the X and via Close |  |
| [`SMS-ADM-VDT-008`](SMS-ADM-VDT-008.cmd) | SmsSendingLogTest | Close View details via ESC / click outside |  |
| [`SMS-ADM-VDT-009`](SMS-ADM-VDT-009.cmd) | SmsSendingLogTest | Show more from View details opens the raw-data modal |  |
| [`SMS-ADM-VDT-010`](SMS-ADM-VDT-010.cmd) | SmsSendingLogTest | Block number from View details opens the Block modal |  |
| [`SMS-ADM-VDT-011`](SMS-ADM-VDT-011.cmd) | SmsSendingLogTest | View details of a Reverse SMS record |  |
| [`SMS-ADM-RAW-001`](SMS-ADM-RAW-001.cmd) | SmsSendingLogTest | Open Show more from the list |  |
| [`SMS-ADM-RAW-002`](SMS-ADM-RAW-002.cmd) | SmsSendingLogTest | Raw data is exactly as stored, with no reformatting |  |
| [`SMS-ADM-RAW-003`](SMS-ADM-RAW-003.cmd) | SmsSendingLogTest | READABLE table maps the raw fields correctly |  |
| [`SMS-ADM-RAW-004`](SMS-ADM-RAW-004.cmd) | SmsSendingLogTest | Raw data with missing, extra, or malformed fields |  |
| [`SMS-ADM-RAW-005`](SMS-ADM-RAW-005.cmd) | SmsSendingLogTest | Copy raw data |  |
| [`SMS-ADM-RAW-006`](SMS-ADM-RAW-006.cmd) | SmsSendingLogTest | Close the Show more modal |  |
| [`SMS-ADM-BLK-001`](SMS-ADM-BLK-001.cmd) | SmsSendingLogTest | Open the Block modal from the list |  |
| [`SMS-ADM-BLK-002`](SMS-ADM-BLK-002.cmd) | SmsSendingLogTest | Reason is required to confirm a block |  |
| [`SMS-ADM-BLK-003`](SMS-ADM-BLK-003.cmd) | SmsSendingLogTest | Reason dropdown offers the spec's options |  |
| [`SMS-ADM-BLK-004`](SMS-ADM-BLK-004.cmd) | SmsSendingLogTest | Note is optional, with a max-length limit |  |
| [`SMS-ADM-BLK-005`](SMS-ADM-BLK-005.cmd) | SmsSendingLogTest | Cancel / X does not block the number |  |
| [`SMS-ADM-BLK-006`](SMS-ADM-BLK-006.cmd) | SmsSendingLogTest | Confirm block - UI result (toast, red label, action becomes Unblock) |  |
| [`SMS-ADM-BLK-007`](SMS-ADM-BLK-007.cmd) | SmsSendingLogTest | Every row of the blocked number shows Blocked |  |
| [`SMS-ADM-BLK-008`](SMS-ADM-BLK-008.cmd) | SmsSendingLogTest | A blocked number cannot request an SMS code |  |
| [`SMS-ADM-BLK-009`](SMS-ADM-BLK-009.cmd) | SmsSendingLogTest | A blocked number cannot request Voice or Reverse SMS |  |
| [`SMS-ADM-BLK-010`](SMS-ADM-BLK-010.cmd) | SmsSendingLogTest | Block applies to all purposes (Registration, Forgot password, Change phone) |  |
| [`SMS-ADM-BLK-011`](SMS-ADM-BLK-011.cmd) | SmsSendingLogTest | Unblock restores the number |  |
| [`SMS-ADM-BLK-012`](SMS-ADM-BLK-012.cmd) | SmsSendingLogTest | Block from Version B (no Show more permission) |  |
| [`SMS-ADM-BLK-013`](SMS-ADM-BLK-013.cmd) | SmsSendingLogTest | Double-clicking Confirm block only blocks once |  |
| [`SMS-ADM-BLK-014`](SMS-ADM-BLK-014.cmd) | SmsSendingLogTest | Concurrent block by two admins on the same number |  |
| [`SMS-ADM-EXP-001`](SMS-ADM-EXP-001.cmd) | SmsSendingLogTest | Export with current filters applied |  |
| [`SMS-ADM-EXP-002`](SMS-ADM-EXP-002.cmd) | SmsSendingLogTest | Export with no data |  |
| [`SMS-ADM-EXP-003`](SMS-ADM-EXP-003.cmd) | SmsSendingLogTest | Block is recorded in the Account Operation Log |  |
| [`SMS-ADM-EXP-004`](SMS-ADM-EXP-004.cmd) | SmsSendingLogTest | Unblock is recorded in the Account Operation Log |  |
| [`SMS-CLI-LIM-001`](SMS-CLI-LIM-001.cmd) | SmsSendingLogTest | Daily limit is 3 codes per number (SMS + Voice) |  |
| [`SMS-CLI-LIM-002`](SMS-CLI-LIM-002.cmd) | SmsSendingLogTest | A failed delivery still counts toward the daily limit |  |
| [`SMS-CLI-LIM-003`](SMS-CLI-LIM-003.cmd) | SmsSendingLogTest | Reverse SMS has no daily limit |  |
| [`SMS-CLI-LIM-004`](SMS-CLI-LIM-004.cmd) | SmsSendingLogTest | Daily count resets at 00:00 HKT |  |
| [`SMS-CLI-LIM-005`](SMS-CLI-LIM-005.cmd) | SmsSendingLogTest | Reset boundary around midnight HKT |  |
| [`SMS-CLI-LIM-006`](SMS-CLI-LIM-006.cmd) | SmsSendingLogTest | The limit is per phone number, not per IP |  |
| [`SMS-CLI-LIM-007`](SMS-CLI-LIM-007.cmd) | SmsSendingLogTest | Code expiry and the Expired verification status |  |
| [`SMS-CLI-LIM-008`](SMS-CLI-LIM-008.cmd) | SmsSendingLogTest | Wrong code entries are logged in history |  |
| [`SMS-CLI-LIM-009`](SMS-CLI-LIM-009.cmd) | SmsSendingLogTest | Verified but not registered is tracked correctly |  |
| [`SMS-CLI-LIM-010`](SMS-CLI-LIM-010.cmd) | SmsSendingLogTest | Username is linked after registration completes |  |
| [`SMS-CLI-LIM-011`](SMS-CLI-LIM-011.cmd) | SmsSendingLogTest | A new front-end request appears in the log in real time |  |
| [`SMS-NFR-UI-001`](SMS-NFR-UI-001.cmd) | SmsSendingLogTest | Layout matches the Figma design |  |
| [`SMS-NFR-UI-002`](SMS-NFR-UI-002.cmd) | SmsSendingLogTest | Responsive layout at smaller resolutions |  |
| [`SMS-NFR-UI-003`](SMS-NFR-UI-003.cmd) | SmsSendingLogTest | Toast auto-dismisses after blocking |  |
| [`SMS-NFR-UI-004`](SMS-NFR-UI-004.cmd) | SmsSendingLogTest | Hover / link styles on row actions |  |
| [`SMS-NFR-UI-005`](SMS-NFR-UI-005.cmd) | SmsSendingLogTest | Cross-browser compatibility (Chrome, Edge, Firefox, Safari) |  |
| [`SMS-NFR-UI-006`](SMS-NFR-UI-006.cmd) | SmsSendingLogTest | Language switch translates every label |  |
| [`SMS-NFR-PRF-001`](SMS-NFR-PRF-001.cmd) | SmsSendingLogTest | Exporting a large dataset (100k+ rows) |  |
| [`SMS-NFR-PRF-002`](SMS-NFR-PRF-002.cmd) | SmsSendingLogTest | Page load time is at most 3 seconds |  |
| [`SMS-NFR-PRF-003`](SMS-NFR-PRF-003.cmd) | SmsSendingLogTest | Search response time is at most 3 seconds |  |
| [`SMS-NFR-PRF-004`](SMS-NFR-PRF-004.cmd) | SmsSendingLogTest | Modal open time is at most 1 second |  |
| [`SMS-NFR-PRF-005`](SMS-NFR-PRF-005.cmd) | SmsSendingLogTest | 50 concurrent admins searching/exporting |  |
| [`SMS-NFR-SEC-001`](SMS-NFR-SEC-001.cmd) | SmsSendingLogTest | Version B - the raw-data API refuses a direct call |  |
| [`SMS-NFR-SEC-002`](SMS-NFR-SEC-002.cmd) | SmsSendingLogTest | Raw data with special characters is escaped (XSS) |  |
| [`SMS-NFR-SEC-003`](SMS-NFR-SEC-003.cmd) | SmsSendingLogTest | Export respects phone masking |  |
| [`SMS-NFR-SEC-004`](SMS-NFR-SEC-004.cmd) | SmsSendingLogTest | Show more / export access is logged |  |
| [`SMS-NFR-SEC-005`](SMS-NFR-SEC-005.cmd) | SmsSendingLogTest | Authorization is enforced on every API, not just the UI |  |
| [`SMS-NFR-SEC-006`](SMS-NFR-SEC-006.cmd) | SmsSendingLogTest | IDOR on record id across tenants |  |
| [`SMS-NFR-SEC-007`](SMS-NFR-SEC-007.cmd) | SmsSendingLogTest | The full phone number is never present in a masked admin's API responses |  |
| [`SMS-NFR-SEC-008`](SMS-NFR-SEC-008.cmd) | SmsSendingLogTest | Sensitive OTP visibility policy in raw data |  |
| [`SMS-NFR-SEC-009`](SMS-NFR-SEC-009.cmd) | SmsSendingLogTest | SQL injection / XSS payloads in search fields are handled safely |  |
| [`SMS-NFR-SEC-010`](SMS-NFR-SEC-010.cmd) | SmsSendingLogTest | CSRF replay on Block / Unblock is rejected |  |
| [`SMS-NFR-REL-001`](SMS-NFR-REL-001.cmd) | SmsSendingLogTest | Time is shown in HKT regardless of the browser's timezone |  |
| [`SMS-NFR-REL-002`](SMS-NFR-REL-002.cmd) | SmsSendingLogTest | SMS provider failure is logged and the page still works |  |
| [`SMS-NFR-REL-003`](SMS-NFR-REL-003.cmd) | SmsSendingLogTest | Summary card totals are consistent with the list |  |
| [`NOT-CLI-021`](NOT-CLI-021.cmd) | UiNotificationCenterClientHK | A visitor who opens the Message Center is kept out - asked to log in or sent away - and sees no message list | known-defect |
| [`NOT-CLI-022`](NOT-CLI-022.cmd) | UiNotificationCenterClientHK | A signed-in member's Message Center lists their messages with title, time and an unread mark, and each tab shows no more than All |  |
| [`NOT-CLI-023`](NOT-CLI-023.cmd) | UiNotificationCenterClientHK | Opening a message shows its details under a Back control, Back returns to the list, and the message is no longer unread |  |
| [`NOT-CLI-024`](NOT-CLI-024.cmd) | UiNotificationCenterClientHK | Mark All as Read is enabled only while something is unread, and pressing it clears every unread mark |  |
| [`NOT-CLI-030`](NOT-CLI-030.cmd) | UiNotificationCenterClientHK | The header's message control takes a signed-in member to the Message Center, and its badge agrees with the unread rows |  |
| [`NOT-CLI-031`](NOT-CLI-031.cmd) | UiNotificationCenterClientHK | Every message belongs to exactly one of System, Deposit and Withdraw - the three tabs together list what All lists |  |
| [`NOT-CLI-032`](NOT-CLI-032.cmd) | UiNotificationCenterClientHK | Under Traditional Chinese the Message Center keeps its structure - four tabs, the same rows - with its labels translated |  |
| [`NOT-CLI-033`](NOT-CLI-033.cmd) | UiNotificationCenterClientHK | After signing out, the Message Center is no longer reachable - the former member is kept out like any visitor |  |
| [`NOT-CLI-034`](NOT-CLI-034.cmd) | UiNotificationCenterClientHK | A message's own address opened without being signed in is refused, not shown |  |
| [`NOT-CLI-035`](NOT-CLI-035.cmd) | UiNotificationCenterClientHK | A signed-in member cannot open another member's message by its own address |  |
| [`NOT-CLI-025`](NOT-CLI-025.cmd) | UiNotificationCenterClientHK | The Message Center renders and works for a member on a PC (Windows, Chrome, 1920x1080) |  |
| [`NOT-CLI-026`](NOT-CLI-026.cmd) | UiNotificationCenterClientHK | The Message Center renders and works for a member on a Mac (Safari/WebKit, 1440x900) |  |
| [`NOT-CLI-027`](NOT-CLI-027.cmd) | UiNotificationCenterClientHK | The Message Center renders and works for a member on an iPad (Safari/WebKit, 820x1180, touch) |  |
| [`NOT-CLI-028`](NOT-CLI-028.cmd) | UiNotificationCenterClientHK | The Message Center renders and works for a member on an iPhone (Safari/WebKit, 390x844, touch) |  |
| [`NOT-CLI-029`](NOT-CLI-029.cmd) | UiNotificationCenterClientHK | The Message Center renders and works for a member on an Android phone (Chrome, 412x915, touch) |  |
| [`MB-CRT-901`](MB-CRT-901.cmd) | MemberBenefitsSeedData | Seed 10 Member Benefits per currency, kept for the data-heavy rows | creates-data |
| [`MB-DEL-901`](MB-DEL-901.cmd) | MemberBenefitsSeedData | Delete every seeded Member Benefit, in every currency | creates-data |
| [`SL-CRT-901`](SL-CRT-901.cmd) | ShareLinkSeedData | Seed 10 Share Links per currency, kept for the data-heavy rows | creates-data |
| [`SL-DEL-901`](SL-DEL-901.cmd) | ShareLinkSeedData | Delete every seeded Share Link, in every currency | creates-data |
| [`SL-ADM-LST-001`](SL-ADM-LST-001.cmd) | ShareLinkTest | Menu navigation |  |
| [`SL-ADM-LST-002`](SL-ADM-LST-002.cmd) | ShareLinkTest | List columns |  |
| [`SL-ADM-LST-003`](SL-ADM-LST-003.cmd) | ShareLinkTest | Default sort by Order ascending |  |
| [`SL-ADM-LST-004`](SL-ADM-LST-004.cmd) | ShareLinkTest | Duplicate Order values are allowed | creates-data |
| [`SL-ADM-LST-005`](SL-ADM-LST-005.cmd) | ShareLinkTest | Language filter defaults to 繁體 |  |
| [`SL-ADM-LST-006`](SL-ADM-LST-006.cmd) | ShareLinkTest | Language filter switches displayed content | creates-data |
| [`SL-ADM-LST-007`](SL-ADM-LST-007.cmd) | ShareLinkTest | A language with no content still shows the row, blank | creates-data |
| [`SL-ADM-LST-008`](SL-ADM-LST-008.cmd) | ShareLinkTest | Status filter (All / Active / Inactive) |  |
| [`SL-ADM-LST-009`](SL-ADM-LST-009.cmd) | ShareLinkTest | Title search |  |
| [`SL-ADM-LST-010`](SL-ADM-LST-010.cmd) | ShareLinkTest | Title search with no result |  |
| [`SL-ADM-LST-011`](SL-ADM-LST-011.cmd) | ShareLinkTest | Combined filters |  |
| [`SL-ADM-LST-012`](SL-ADM-LST-012.cmd) | ShareLinkTest | Show entries & pagination |  |
| [`SL-ADM-LST-013`](SL-ADM-LST-013.cmd) | ShareLinkTest | Refresh icon reloads the list |  |
| [`SL-ADM-LST-014`](SL-ADM-LST-014.cmd) | ShareLinkTest | URL link column is clickable and underlined |  |
| [`SL-ADM-LST-015`](SL-ADM-LST-015.cmd) | ShareLinkTest | Status badge and thumbnail |  |
| [`SL-ADM-LST-016`](SL-ADM-LST-016.cmd) | ShareLinkTest | Last updated reflects the save time | creates-data |
| [`SL-ADM-LST-017`](SL-ADM-LST-017.cmd) | ShareLinkTest | Add button opens the Add page |  |
| [`SL-ADM-LST-018`](SL-ADM-LST-018.cmd) | ShareLinkTest | Modify button opens prefilled |  |
| [`SL-ADM-LST-019`](SL-ADM-LST-019.cmd) | ShareLinkTest | Long content does not break the list layout | creates-data |
| [`SL-ADM-ADD-001`](SL-ADM-ADD-001.cmd) | ShareLinkTest | Page layout |  |
| [`SL-ADM-ADD-002`](SL-ADM-ADD-002.cmd) | ShareLinkTest | Return without save creates no record |  |
| [`SL-ADM-ADD-003`](SL-ADM-ADD-003.cmd) | ShareLinkTest | Valid add - manual 3 languages | creates-data |
| [`SL-ADM-ADD-004`](SL-ADM-ADD-004.cmd) | ShareLinkTest | Valid add - override 简体 & English | creates-data |
| [`SL-ADM-ADD-005`](SL-ADM-ADD-005.cmd) | ShareLinkTest | Valid add - All languages override | creates-data |
| [`SL-ADM-ADD-006`](SL-ADM-ADD-006.cmd) | ShareLinkTest | Optional language (日語) filled manually | creates-data |
| [`SL-ADM-ADD-007`](SL-ADM-ADD-007.cmd) | ShareLinkTest | Active is unticked by default |  |
| [`SL-ADM-ADD-008`](SL-ADM-ADD-008.cmd) | ShareLinkTest | Saving Inactive | creates-data |
| [`SL-ADM-ADD-009`](SL-ADM-ADD-009.cmd) | ShareLinkTest | Override replaces existing content | creates-data |
| [`SL-ADM-ADD-010`](SL-ADM-ADD-010.cmd) | ShareLinkTest | Override from a non-繁體 tab excludes the current tab | creates-data |
| [`SL-ADM-ADD-011`](SL-ADM-ADD-011.cmd) | ShareLinkTest | All languages checkbox ticks/unticks with its members |  |
| [`SL-ADM-ADD-012`](SL-ADM-ADD-012.cmd) | ShareLinkTest | Override copies the image too | creates-data |
| [`SL-ADM-ADD-013`](SL-ADM-ADD-013.cmd) | ShareLinkTest | Switching tabs keeps entered data | creates-data |
| [`SL-ADM-ADD-014`](SL-ADM-ADD-014.cmd) | ShareLinkTest | Double submit creates only one record | creates-data |
| [`SL-ADM-VAL-001`](SL-ADM-VAL-001.cmd) | ShareLinkTest | Title counter updates live |  |
| [`SL-ADM-VAL-002`](SL-ADM-VAL-002.cmd) | ShareLinkTest | Title at the 20-character boundary is accepted | creates-data |
| [`SL-ADM-VAL-003`](SL-ADM-VAL-003.cmd) | ShareLinkTest | Title over 20 characters is rejected |  |
| [`SL-ADM-VAL-004`](SL-ADM-VAL-004.cmd) | ShareLinkTest | A 1-character Title is accepted | creates-data |
| [`SL-ADM-VAL-005`](SL-ADM-VAL-005.cmd) | ShareLinkTest | A blank / spaces-only Title is rejected |  |
| [`SL-ADM-VAL-006`](SL-ADM-VAL-006.cmd) | ShareLinkTest | Multi-byte Title characters (CJK/Thai accepted, emoji rejected) |  |
| [`SL-ADM-VAL-007`](SL-ADM-VAL-007.cmd) | ShareLinkTest | Text content at the 60-character boundary is accepted | creates-data |
| [`SL-ADM-VAL-008`](SL-ADM-VAL-008.cmd) | ShareLinkTest | Text content over 60 characters is rejected |  |
| [`SL-ADM-VAL-009`](SL-ADM-VAL-009.cmd) | ShareLinkTest | Blank Text content is rejected |  |
| [`SL-ADM-VAL-010`](SL-ADM-VAL-010.cmd) | ShareLinkTest | A line break in Text content is treated as a space |  |
| [`SL-ADM-VAL-011`](SL-ADM-VAL-011.cmd) | ShareLinkTest | Valid image formats - JPG, PNG, WEBP | creates-data |
| [`SL-ADM-VAL-012`](SL-ADM-VAL-012.cmd) | ShareLinkTest | Image size limit - 2 MB accepted, 2 MB + 1 byte rejected |  |
| [`SL-ADM-VAL-013`](SL-ADM-VAL-013.cmd) | ShareLinkTest | Invalid image formats are rejected |  |
| [`SL-ADM-VAL-014`](SL-ADM-VAL-014.cmd) | ShareLinkTest | Minimum resolution is a recommendation, not enforced | creates-data |
| [`SL-ADM-VAL-015`](SL-ADM-VAL-015.cmd) | ShareLinkTest | A non-16:9 image is accepted (cropped on the client) | creates-data |
| [`SL-ADM-VAL-016`](SL-ADM-VAL-016.cmd) | ShareLinkTest | Only one image is kept - the second upload replaces the first | creates-data |
| [`SL-ADM-VAL-017`](SL-ADM-VAL-017.cmd) | ShareLinkTest | Delete image leaves Card image required again |  |
| [`SL-ADM-VAL-018`](SL-ADM-VAL-018.cmd) | ShareLinkTest | A valid https URL with query and fragment is accepted | creates-data |
| [`SL-ADM-VAL-019`](SL-ADM-VAL-019.cmd) | ShareLinkTest | A URL with no scheme is rejected |  |
| [`SL-ADM-VAL-020`](SL-ADM-VAL-020.cmd) | ShareLinkTest | An http:// URL is rejected |  |
| [`SL-ADM-VAL-021`](SL-ADM-VAL-021.cmd) | ShareLinkTest | Dangerous URL schemes are rejected |  |
| [`SL-ADM-VAL-022`](SL-ADM-VAL-022.cmd) | ShareLinkTest | The bare "https://" prefix alone is rejected |  |
| [`SL-ADM-VAL-023`](SL-ADM-VAL-023.cmd) | ShareLinkTest | Long / IDN / space-padded URLs follow the standard rule |  |
| [`SL-ADM-VAL-024`](SL-ADM-VAL-024.cmd) | ShareLinkTest | Order accepts positive integers, rejects negative/decimal/letters |  |
| [`SL-ADM-VAL-025`](SL-ADM-VAL-025.cmd) | ShareLinkTest | Required-field validation on an empty form |  |
| [`SL-ADM-VAL-026`](SL-ADM-VAL-026.cmd) | ShareLinkTest | Error box message names the invalid/empty languages |  |
| [`SL-ADM-VAL-027`](SL-ADM-VAL-027.cmd) | ShareLinkTest | Fixing a language's content clears its error marker |  |
| [`SL-ADM-VAL-028`](SL-ADM-VAL-028.cmd) | ShareLinkTest | An optional language with only Title filled still saves | creates-data |
| [`SL-ADM-MOD-001`](SL-ADM-MOD-001.cmd) | ShareLinkTest | Prefilled data |  |
| [`SL-ADM-MOD-002`](SL-ADM-MOD-002.cmd) | ShareLinkTest | Editing 繁體 Title | creates-data |
| [`SL-ADM-MOD-003`](SL-ADM-MOD-003.cmd) | ShareLinkTest | Editing only one language leaves the others unchanged | creates-data |
| [`SL-ADM-MOD-004`](SL-ADM-MOD-004.cmd) | ShareLinkTest | Replacing the image | creates-data |
| [`SL-ADM-MOD-005`](SL-ADM-MOD-005.cmd) | ShareLinkTest | Changing the URL | creates-data |
| [`SL-ADM-MOD-006`](SL-ADM-MOD-006.cmd) | ShareLinkTest | Changing Order re-sorts the list | creates-data |
| [`SL-ADM-MOD-007`](SL-ADM-MOD-007.cmd) | ShareLinkTest | Deactivating | creates-data |
| [`SL-ADM-MOD-008`](SL-ADM-MOD-008.cmd) | ShareLinkTest | Reactivating | creates-data |
| [`SL-ADM-MOD-009`](SL-ADM-MOD-009.cmd) | ShareLinkTest | Override helper text on Modify | creates-data |
| [`SL-ADM-MOD-010`](SL-ADM-MOD-010.cmd) | ShareLinkTest | No override leaves other languages' own content untouched | creates-data |
| [`SL-ADM-MOD-011`](SL-ADM-MOD-011.cmd) | ShareLinkTest | Overriding a required language on Modify replaces only that one | creates-data |
| [`SL-ADM-MOD-012`](SL-ADM-MOD-012.cmd) | ShareLinkTest | Clearing a required field is refused | creates-data |
| [`SL-ADM-MOD-013`](SL-ADM-MOD-013.cmd) | ShareLinkTest | Clearing an optional language removes its content | creates-data |
| [`SL-ADM-MOD-014`](SL-ADM-MOD-014.cmd) | ShareLinkTest | Return without save discards the edit | creates-data |
| [`SL-ADM-MOD-015`](SL-ADM-MOD-015.cmd) | ShareLinkTest | The record is deleted by another admin while this one is editing it |  |
| [`SL-ADM-MOD-016`](SL-ADM-MOD-016.cmd) | ShareLinkTest | Two admins modify the same record concurrently |  |
| [`SL-ADM-MOD-017`](SL-ADM-MOD-017.cmd) | ShareLinkTest | Required languages are enforced on Modify too | creates-data |
| [`SL-ADM-DEL-001`](SL-ADM-DEL-001.cmd) | ShareLinkTest | Confirm modal content | creates-data |
| [`SL-ADM-DEL-002`](SL-ADM-DEL-002.cmd) | ShareLinkTest | Cancel or ✕ closes the modal without deleting | creates-data |
| [`SL-ADM-DEL-003`](SL-ADM-DEL-003.cmd) | ShareLinkTest | Confirming delete removes the record everywhere | creates-data |
| [`SL-ADM-DEL-004`](SL-ADM-DEL-004.cmd) | ShareLinkTest | Deleting the only record on the last page redirects to page 1 |  |
| [`SL-ADM-DEL-005`](SL-ADM-DEL-005.cmd) | ShareLinkTest | Deleting an Inactive record | creates-data |
| [`SL-ADM-DEL-006`](SL-ADM-DEL-006.cmd) | ShareLinkTest | A deleted record's Modify URL is not reachable |  |
| [`SL-NFR-UI-001`](SL-NFR-UI-001.cmd) | ShareLinkTest | BO UI vs Figma |  |
| [`SL-NFR-UI-002`](SL-NFR-UI-002.cmd) | ShareLinkTest | Client UI vs Figma & responsive widths |  |
| [`SL-NFR-UI-003`](SL-NFR-UI-003.cmd) | ShareLinkTest | Long unbroken text does not break the BO layout | creates-data |
| [`SL-NFR-UI-004`](SL-NFR-UI-004.cmd) | ShareLinkTest | A broken image falls back to a placeholder, not a broken layout | creates-data |
| [`SL-NFR-UI-005`](SL-NFR-UI-005.cmd) | ShareLinkTest | BO cross-browser compatibility |  |
| [`SL-NFR-UI-006`](SL-NFR-UI-006.cmd) | ShareLinkTest | Client device compatibility |  |
| [`SL-NFR-UI-007`](SL-NFR-UI-007.cmd) | ShareLinkTest | New tab opens in restricted browsers/webviews |  |
| [`SL-NFR-UI-008`](SL-NFR-UI-008.cmd) | ShareLinkTest | Invalid submit scrolls/focuses the first error |  |
| [`SL-NFR-UI-009`](SL-NFR-UI-009.cmd) | ShareLinkTest | Keyboard accessibility of the Add form |  |
| [`SL-NFR-UI-010`](SL-NFR-UI-010.cmd) | ShareLinkTest | Toast messages after Modify and Delete | creates-data |
| [`SL-NFR-PRF-001`](SL-NFR-PRF-001.cmd) | ShareLinkTest | List loads within 3 seconds |  |
| [`SL-NFR-PRF-002`](SL-NFR-PRF-002.cmd) | ShareLinkTest | A 2 MB image uploads within 5 seconds |  |
| [`SL-NFR-PRF-003`](SL-NFR-PRF-003.cmd) | ShareLinkTest | Client load performance on throttled 4G |  |
| [`SL-NFR-SEC-001`](SL-NFR-SEC-001.cmd) | ShareLinkTest | XSS payloads render as plain text | creates-data |
| [`SL-NFR-SEC-002`](SL-NFR-SEC-002.cmd) | ShareLinkTest | Server-side URL validation (direct API) |  |
| [`SL-NFR-SEC-003`](SL-NFR-SEC-003.cmd) | ShareLinkTest | Server-side field validation (direct API) |  |
| [`SL-NFR-SEC-004`](SL-NFR-SEC-004.cmd) | ShareLinkTest | Go to Link uses rel="noopener noreferrer" |  |
| [`SL-NFR-SEC-005`](SL-NFR-SEC-005.cmd) | ShareLinkTest | Malicious uploads are rejected server-side | creates-data |
| [`SL-NFR-SEC-006`](SL-NFR-SEC-006.cmd) | ShareLinkTest | Permission control for an admin without Share link access |  |
| [`SL-NFR-SEC-007`](SL-NFR-SEC-007.cmd) | ShareLinkTest | Unauthenticated access is refused |  |
| [`SL-NFR-SEC-008`](SL-NFR-SEC-008.cmd) | ShareLinkTest | SQL injection in the title search is treated as text |  |
| [`SL-NFR-SEC-009`](SL-NFR-SEC-009.cmd) | ShareLinkTest | CSRF protection |  |
| [`SL-NFR-SEC-010`](SL-NFR-SEC-010.cmd) | ShareLinkTest | Session timeout on an idle Add page |  |
| [`SL-NFR-REL-001`](SL-NFR-REL-001.cmd) | ShareLinkTest | A save is atomic even if the network drops mid-request |  |
| [`SL-NFR-REL-002`](SL-NFR-REL-002.cmd) | ShareLinkTest | Unicode content round-trips in all 12 languages | creates-data |
| [`SL-NFR-REL-003`](SL-NFR-REL-003.cmd) | ShareLinkTest | Last updated matches the BO header's timezone and format | creates-data |

940 cases.
