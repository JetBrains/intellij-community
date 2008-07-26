package org.jetbrains.plugins.ruby.testing.testunit.runner.states;

import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.ui.PrintableTestProxy;
import com.intellij.execution.ui.ConsoleViewContentType;

/**
 * @author Roman Chernyatchik
 */
public class TestFailedState extends AbstractState {
  private final String myPresentationText;

  public TestFailedState(final String localizedMessage, final String stackTrace) {
    myPresentationText = localizedMessage + PrintableTestProxy.NEW_LINE + stackTrace + PrintableTestProxy.NEW_LINE;
  }

  @Override
  public void printOn(final Printer printer) {
    printer.print(myPresentationText, ConsoleViewContentType.ERROR_OUTPUT);
  }

  public boolean isDefect() {
    return true;
  }

  public boolean wasLaunched() {
    return true;
  }

  public boolean isFinal() {
    return true;
  }

  public boolean isInProgress() {
    return false;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "TEST FAILED";
  }
}
