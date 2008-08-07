package org.jetbrains.plugins.ruby.testing.testunit.runner.states;

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
    SKIPPED_INDEX(0),
    COMPLETE_INDEX(1),
    NOT_RUN_INDEX(2),
    RUNNING_INDEX(3),
    TERMINATED_INDEX(4),
    IGNORED_INDEX(5),
    FAILED_INDEX(6),
    ERROR_INDEX(8),
    PASSED_INDEX(COMPLETE_INDEX.myValue);

    private final int myValue;

    Magnitude(int value) {
      myValue = value;
    }

    public int getValue() {
      return myValue;
    }
  }
}
