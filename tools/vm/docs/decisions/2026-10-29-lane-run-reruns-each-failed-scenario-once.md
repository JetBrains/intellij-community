---
topic: testing
---

# lane-run reruns each failed scenario once

Date: 2026-10-29

## Status

Accepted. Issue [IJAI-5259](https://youtrack.jetbrains.com/issue/IJAI-5259). The rules are in the `lane-run` part of
the [lane controller spec](../../spec/lane-controller.spec.md). The TeamCity half needs the failure condition "Support
test retry" on the bucket configurations, which IJ infra sets.

## Context

A TeamCity Air UI bucket runs one Bazel test per lane, and `lane-run` runs every scenario of the lane in it. When one
scenario fails, the retry policy of the parent project cancels the build and starts it again. Build 1111146950 of
`Air Plugin UI Tests / 1` failed 1 of 88 scenarios, and two more builds of the same bucket followed. Each failure was
a scenario that fails on other branches at 0.3 to 5 %. The chain took about 1 h 50 min instead of 70 min. Bazel
`--flaky_test_attempts` runs the whole target again, so it costs the same 32 minutes.

The JPS runner reruns only its failed tests (`intellij.build.test.attempt.count`, `OutOfProcessRetries`), and its
`Retries` class says that TeamCity needs "test retry support" to mute the earlier failure.

## Decision

1. **`lane-run` reruns each failed scenario of the lane IDE once, in the same Bazel test.** The reruns start after the
   last suite of the lane IDE and before the suites of a bare IDE, so the lane IDE is still up. The lane daemon of a
   worker reruns nothing: a developer who runs `vm.cmd run` sees each flake.

2. **TeamCity reads both attempts from one `test.xml`.** The TeamCity Bazel plugin reads the build event file and
   imports each `test.xml` with `##teamcity[importData type='junit' path=...]`. Build 1111146950 shows the line
   "Importing data from '...test.xml' with 'junit' processor". The Ant JUnit parser of the XML report plugin
   (`AntJUnitReportParser` in `JetBrains/teamcity-xml-tests-reporting`) opens one test block for each `<testcase>`, in
   the order of the file, with no check for a name it saw before. So two cases of one class and name are two runs of
   one test in the build, as two `testStarted` messages of one name are. The TeamCity documentation of flaky tests
   names the same effect for Surefire: a parameterized test that writes cases of one name in one report counts as
   several runs of one test. The failure condition "if at least one test failed" has the option "support test retry:
   successful test run mutes previous test failure" (Kotlin DSL `failureConditions { supportTestRetry = true }`).
   With it, "TeamCity will mute a test if it fails and then succeeds within the same build", and "such tests will not
   affect the build status". The XML report plugin reads no `flakyFailure` or `rerunFailure` element of Surefire, so
   one case with two outcomes is no option.

3. **The verdict of `lane-run` follows the last attempt.** A pass of a rerun takes the failure of the first attempt out
   of the counts of the stream, so the summary has no failure, `lane-run` exits 0, and Bazel passes the test. Without
   that, Bazel fails the step on its exit code whatever TeamCity mutes. The JUnit XML keeps the failed attempt, because
   TeamCity needs it to show the test as flaky. bt counts the same `test.xml`, so bt reads a failed case that a later
   case of the same name passed as flaky. The result of the run names only the failures that stayed.

4. **A rerun runs on a recycled IDE.** Each scenario already starts with a reset that recycles the IDE when the reset
   does not hold. The reset clears only the state that it knows of, and IJAI-4926 showed a red scenario that the
   state of an earlier scenario in the shared IDE caused. A new IDE process on the same context removes the JVM state
   that no reset reaches. The recycle costs about one IDE start, once for all reruns, which is small beside the 32
   minutes of a restarted bucket. The recycle is not part of the recycle budget of the isolation.

5. **More than 3 failures rerun nothing.** Of the last 300 builds of each of the two UI buckets, 381 passed, 156
   failed 1 test, 28 failed 2, 2 failed 3, 1 failed 4 and 1 failed 5. The builds with 4 and 5 failures were Safe Push
   checks that broke the product. A limit of 3 covers 186 of the 188 builds with failed tests, and the rerun of 3
   scenarios costs about two minutes. The limit counts the failed suite prologues too. The limit is a constant: no
   caller needs another value yet.

6. **The rerun spends the budget of the run.** It starts only when the budget left covers the recycle, 13 minutes.
   The watchdog ends a rerun at the end of the budget as it ends any scenario, so a cut-off run keeps its
   `lane-run-budget` build problem. A run that cannot afford the rerun fails as before, and the retry policy of the
   build still applies.

7. **No rerun where a rerun is wrong.** The live lane spends the turns of real accounts. A bare IDE measures its own
   start-up, and its rerun needs a new bare IDE. A failed prologue, an aborted scenario and a skipped scenario have no
   verdict to retry.

8. **`AIR_LANE_INJECT_FAILURE` proves the rerun.** The value `first:<scenario>` fails the first attempt and
   `every:<scenario>` each attempt. A Bazel test gets it with `--test_env`. So a local run, and a personal build of
   IJ infra, can show a flaky test and a test that fails twice with a real lane IDE.

## Consequences

- A bucket with one flaky scenario costs one more scenario and one IDE start, not a new build.
- Until IJ infra sets "Support test retry" on `Air Plugin UI Tests / <n>` and the gui-chat bucket configurations, a
  failed attempt still fails the build on "at least one test failed", and the retry policy restarts it as today.
- A green run that failed an attempt keeps its evidence under `test.outputs/lane`, and `lane-run` publishes the video
  and the failure picture of the failed attempt as before. The recorder already writes each attempt as a bundle of its
  own, and the second takes the stem `<scenario>.2`.
- The summary counts the scenarios whose last attempt failed. `testsStarted` counts each attempt.

## Alternatives rejected

- **Bazel `--flaky_test_attempts`.** It runs all scenarios of the target again.
- **A rerun on the IDE as it is, with the reset only.** It is the same isolation that the next scenario gets, and a
  flake that the state of the run caused fails again.
- **A rerun in the lane daemon too.** A developer then sees a green run where a scenario fails 1 time in 2.
- **One case with a `flakyFailure` element.** The XML report plugin of TeamCity does not read it.
