package com.intellij.execution.testframework.sm.runner.states;

import org.jetbrains.annotations.NotNull;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;

import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class SuiteInProgressState extends TestInProgressState {
  private final SMTestProxy mySuiteProxy;
  private Boolean isDefectWasReallyFound = null; // null - is unset

  public SuiteInProgressState(@NotNull final SMTestProxy suiteProxy) {
    mySuiteProxy = suiteProxy;
  }
  
  ///**
  // * If any of child failed proxy also is deffect
  // * @return
  // */
  @Override
  public boolean isDefect() {
    if (isDefectWasReallyFound != null) {
      return isDefectWasReallyFound.booleanValue();
    }

     //Test suit fails if any of its tests fails
    final List<? extends SMTestProxy> children = mySuiteProxy.getChildren();
    for (SMTestProxy child : children) {
      if (child.isDefect()) {
        isDefectWasReallyFound = true;
        return true;
      }
    }

    //cannot cache because one of child tests may fail in future
    return false;
  }

  public boolean wasTerminated() {
    return false;
  }

  public Magnitude getMagnitude() {
    return Magnitude.RUNNING_INDEX;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "SUITE PROGRESS";
  }
}
