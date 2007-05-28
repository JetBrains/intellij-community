package com.theoryinpractice.testng.ui;

import com.intellij.execution.junit2.states.DiffHyperlink;
import com.intellij.execution.junit2.ui.ConsoleViewPrinter;
import com.intellij.execution.ui.ConsoleView;
import com.theoryinpractice.testng.model.TestNGConsoleProperties;

/**
 * @author Hani Suleiman Date: Dec 1, 2006 Time: 12:14:04 PM
 */
public class TestNGDiffHyperLink extends DiffHyperlink implements Printable {
  private final TestNGConsoleProperties myConsoleProperties;

  public TestNGDiffHyperLink(final String expected, final String actual, final String filePath, final TestNGConsoleProperties consoleProperties) {
    super(expected, actual, filePath);
    myConsoleProperties = consoleProperties;
  }


  public void print(ConsoleView printer) {
    printOn(new ConsoleViewPrinter(printer, myConsoleProperties));
  }
}