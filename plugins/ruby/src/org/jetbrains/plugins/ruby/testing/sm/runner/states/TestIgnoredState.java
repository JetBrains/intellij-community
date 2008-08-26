package org.jetbrains.plugins.ruby.testing.sm.runner.states;

import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.ui.PrintableTestProxy;
import com.intellij.execution.ui.ConsoleViewContentType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.ruby.RBundle;

/**
 * @author Roman Chernyatchik
 */
public class TestIgnoredState extends AbstractState {
  @NonNls private static final String IGNORED_TEST_TEXT = RBundle.message("ruby.test.runner.states.test.is.ignored");
  private final String myText;

  public TestIgnoredState(final String ignoredComment) {
    myText = PrintableTestProxy.NEW_LINE + IGNORED_TEST_TEXT + ' ' + ignoredComment;
  }

  public boolean isInProgress() {
    return false;
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

  public boolean wasTerminated() {
    return false;
  }

  public Magnitude getMagnitude() {
    return Magnitude.IGNORED_INDEX;
  }

  @Override
  public void printOn(final Printer printer) {
    super.printOn(printer);

    printer.print(myText, ConsoleViewContentType.SYSTEM_OUTPUT);
  }


  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "TEST IGNORED";
  }
}