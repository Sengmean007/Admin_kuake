# FILED as GD-149 on 2026-10-02 - https://jira.initcapp.com/browse/GD-149 (laid out per "GD QA Ticket Checklist", 1 Oct 2026)

| Field | Value | Checklist |
|---|---|---|
| **Project** | GD | |
| **Issue type** | Bug (故障) | always - not a subtask |
| **Summary** | [Regression] Auto Greeting Message fails to trigger after 30-minute interval when Unsettled Rebate Claim exists | always |
| **Defect Environment** | Test | always |
| **Linked Issues** | found in → **GD-60** [Backend] Implement notification triggers, scheduling and targeting *(the Notification Pushing story - confirm, or name the story/task you were testing)* | always, when found while testing - "found in", not "Relates" |
| **Regression link** | none - "is caused by" is for production bugs only; this was found on Test | production bugs only |
| **QA Owner** | sengmean (you) | always |
| **Acceptance Criteria** | After the 30-minute cycle elapses, the Auto Greeting Message is sent to Customer Service for every eligible user whose Rebate Claim is Unsettled, with the Notification Pushing feature enabled. | always - the expected result |
| **Priority** | High | |
| **Affects Version/s** | Platform V5.0.0 | |
| **Assignee** | left for the team lead / developer | never reassign to QA |

## Description
```
Description:
- Detail of issue (Actual result):
  1. [Functional] With the new Notification Pushing feature enabled, the
     Auto Greeting Message configured for Customer Service on a 30-minute
     cycle does not fire: the 30-minute interval completes and no auto
     greeting message is triggered or delivered to Customer Service for
     a user who holds an Unsettled Rebate Claim.
     Module / Feature: Customer Service Automation / Notification Pushing /
     Rebate Claim. Trigger interval: 30-minute cycle.

- Pre-condition:
  1. Environment: Test (QA)
  2. The user account has an active Rebate Claim with a value present
  3. That Rebate Claim's status is Unsettled
  4. Auto Greeting Message is enabled and set to a 30-minute repeat cycle
  5. The new Notification Pushing feature is enabled / deployed for
     customer alerts

- Test Steps:
  1. Log in to the platform in the Test environment
  2. Make sure the user meets the pre-condition: a Rebate Claim is
     present with status Unsettled
  3. Configure the Auto Greeting Message for Customer Services with a
     30-minute trigger cycle
  4. Enable / deploy the new Notification Pushing feature for customer
     alerts
  5. Wait for the 30-minute cycle interval to elapse
  6. Check the Customer Service chat window / messaging log for the auto
     greeting message

- Screenshot of issue:
  1. (attach the Customer Service chat window / messaging log after the
     30-minute cycle, showing no greeting message)

- Expected result:
  1. After 30 minutes the background trigger runs and the Auto Greeting
     Message is sent to Customer Service for eligible users with an
     Unsettled Rebate Claim.

- Impact:
  Severity High / Major. A critical automated customer engagement flow
  is broken following the integration of the Notification Pushing
  service: users with unsettled rebates are not receiving their
  scheduled automated touchpoints.
```

## After filing (checklist section 3)
- Retest when the developer moves it to Resolved: fix works → Passed TEST, then Passed Staging/PROD after round 2; fix fails → Re-Open.
- Never move it to Resolved or Done yourself, and keep the developer as assignee.
