# lane-run reruns each failed scenario of the lane IDE once, in the same Bazel test

## Context

A TeamCity Air UI bucket runs one Bazel test per lane, and `lane-run` runs every scenario of the lane in it. When one
scenario fails, the retry policy of the parent project starts the whole build again. Most such failures are flakes of
one scenario. Bazel `--flaky_test_attempts` also runs the whole target again.

The TeamCity Bazel plugin imports each `test.xml` with the JUnit processor. That parser opens one test block for each
`<testcase>`, in the order of the file. So two cases of one class and name are two runs of one test. The failure
condition "Support test retry" then mutes a failure that a later run of the same test in the build passes. IJ infra
sets that condition on the bucket configurations.

The requirements are in [the lane-daemon verb](../../../../spec/lane-controller.spec.md#the-lane-daemon-verb). The code
is [rerun.rs](../../../../crates/avl-guest/src/lane/rerun.rs).

## Decisions

1. **`lane-run` reruns each failed scenario of the lane IDE once, in the same Bazel test.** The reruns start after the
   last suite of the lane IDE and before the suites of a bare IDE. The lane daemon of a worker reruns nothing, so a
   developer who runs `vm.cmd run` sees each flake.
2. **TeamCity reads both attempts from one `test.xml`.** The JUnit XML holds the failed attempt and the rerun as two
   cases of one name.
3. **The verdict of `lane-run` follows the last attempt.** A pass of the rerun takes the first failure out of the counts
   of the stream. The summary then has no failure, `lane-run` exits 0, and Bazel passes the test. The JUnit XML keeps
   the failed attempt, so TeamCity and bt show the test as flaky.
4. **A rerun runs on a recycled IDE.** The reset of a scenario clears only the state that it knows of. A new IDE process
   on the same context removes the JVM state that no reset reaches. The recycle runs once for all reruns, and it is not
   part of the recycle budget of the isolation.
5. **More than 3 failures rerun nothing.** More failures are a break of the product, not a flake. The limit counts the
   failed suite prologues too, and it is a constant.
6. **The rerun spends the budget of the run.** It starts only when the budget left covers the recycle. The watchdog ends
   a rerun at the end of the budget, so a cut-off run keeps its `lane-run-budget` build problem.
7. **No rerun where a rerun is wrong.** The live lane spends the turns of real accounts. A bare IDE measures its own
   start-up. A failed prologue, an aborted scenario and a skipped scenario have no verdict to retry.
8. **`AIR_LANE_INJECT_FAILURE` proves the rerun.** The value `first:<scenario>` fails the first attempt, and
   `every:<scenario>` fails each attempt. A Bazel test gets it with `--test_env`.

## Alternatives rejected

- **Bazel `--flaky_test_attempts`.** It runs all scenarios of the target again.
- **A rerun on the IDE as it is, with the reset only.** A flake that the state of the run caused fails again.
- **A rerun in the lane daemon too.** A developer then sees a green run where a scenario fails 1 time in 2.
- **One case with a `flakyFailure` element.** The XML report plugin of TeamCity does not read it.

## Consequences

- A bucket with one flaky scenario costs one more scenario and one IDE start, not a new build.
- Without "Support test retry" on a bucket configuration, a failed attempt still fails the build.
- A green run that failed an attempt keeps its evidence under `test.outputs/lane`. `lane-run` publishes the video and
  the failure picture of the failed attempt. The second attempt takes the bundle stem `<scenario>.2`.
- The summary counts the scenarios whose last attempt failed. `testsStarted` counts each attempt.
