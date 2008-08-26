package org.jetbrains.plugins.ruby.testing.sm.runner.states;

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
    super.printOn(printer);

    printer.print(PrintableTestProxy.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    printer.mark();
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

  public boolean wasTerminated() {
    return false;
  }

  public Magnitude getMagnitude() {
    return Magnitude.FAILED_INDEX;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "TEST FAILED";
  }
}
