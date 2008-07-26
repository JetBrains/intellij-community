package org.jetbrains.plugins.ruby.testing.testunit.runner.states;

import com.intellij.execution.testframework.Printable;
import com.intellij.execution.testframework.Printer;

/**
 * @author Roman Chernyatchik
 *
 * Inheritors of this class describes concreate states of tests
 * with additional info e.g. stacktraces for failed state or
 * ignored message for ignored state
 */
public abstract class AbstractState implements Printable, TestStateInfo {
  public void printOn(final Printer printer) {
    // Do nothing by default
  }
}
