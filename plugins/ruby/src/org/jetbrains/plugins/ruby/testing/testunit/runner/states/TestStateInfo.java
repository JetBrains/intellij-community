package org.jetbrains.plugins.ruby.testing.testunit.runner.states;

import org.jetbrains.plugins.ruby.RBundle;

/**
 * @author Roman Chernyatchik
 *
 * Describes properties of tests/suites states
 */
public interface TestStateInfo {
  /**
   * @return If test/test suite is running
   */
  boolean isInProgress();

  /**
   * Some magic definition from AbstractTestProxy class.
   * If state is defect something wrong is with it and should be shown
   * properly in UI.
   * @return
   */
  boolean isDefect();

  /**
   * @return True if test/suite has been already launched
   */
  boolean wasLaunched();

  /**
   * Describes final states, e.g such states will not be
   * changed after finished event.
   * @return True if is final
   */
  boolean isFinal();

  /**
   * @return Was terminated by user
   */
  boolean wasTerminated();

  /**
   * It's some magic parameter than describe state type.
   * @return
   */
  Magnitude getMagnitude();

  //WARN: It is Hack, see PoolOfTestStates, API is necessary
  enum Magnitude {
    SKIPPED_INDEX(0, RBundle.message("ruby.test.runner.magnitude.skipped.failed.title")),
    COMPLETE_INDEX(1, RBundle.message("ruby.test.runner.magnitude.completed.failed.title")),
    NOT_RUN_INDEX(2, RBundle.message("ruby.test.runner.magnitude.not.run.failed.title")),
    RUNNING_INDEX(3, RBundle.message("ruby.test.runner.magnitude.running.failed.title")),
    TERMINATED_INDEX(4, RBundle.message("ruby.test.runner.magnitude.terminated.failed.title")),
    IGNORED_INDEX(5, RBundle.message("ruby.test.runner.magnitude.ignored.failed.title")),
    FAILED_INDEX(6, RBundle.message("ruby.test.runner.magnitude.assertion.failed.title")),
    ERROR_INDEX(8, RBundle.message("ruby.test.runner.magnitude.testerror.title")),
    PASSED_INDEX(COMPLETE_INDEX.myValue, RBundle.message("ruby.test.runner.magnitude.passed.title"));

    private final int myValue;
    private final String myTitle;

    Magnitude(final int value, final String title) {
      myValue = value;
      myTitle = title;
    }

    public int getValue() {
      return myValue;
    }

    public String getTitle() {
      return myTitle;
    }
  }
}
