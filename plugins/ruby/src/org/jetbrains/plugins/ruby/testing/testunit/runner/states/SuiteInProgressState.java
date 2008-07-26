package org.jetbrains.plugins.ruby.testing.testunit.runner.states;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;

import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class SuiteInProgressState extends TestInProgressState {
  private RTestUnitTestProxy mySuiteProxy;
  private Boolean isDefectWasReallyFound = null; // null - is unset

  public SuiteInProgressState(@NotNull final RTestUnitTestProxy suiteProxy) {
    mySuiteProxy = suiteProxy;
  }

  /**
   * If any of child failed proxy also is deffect
   * @return
   */
  @Override
  public boolean isDefect() {
    if (isDefectWasReallyFound != null) {
      return isDefectWasReallyFound.booleanValue();
    }

    // Test suit fails if any of its tests fails
    final List<? extends RTestUnitTestProxy> children = mySuiteProxy.getChildren();
    for (RTestUnitTestProxy child : children) {
      if (child.isDefect()) {
        isDefectWasReallyFound = true;
        return true;
      }
    }

    //cannot cache because one of child tests may fail in future
    return false;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "SUITE PROGRESS";
  }
}
