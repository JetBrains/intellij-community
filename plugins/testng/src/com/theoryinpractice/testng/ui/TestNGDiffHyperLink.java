package com.theoryinpractice.testng.ui;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.junit2.*;
import com.intellij.execution.junit2.states.DiffHyperlink;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.theoryinpractice.testng.model.TestNGConsoleProperties;

/**
 * @author Hani Suleiman Date: Dec 1, 2006 Time: 12:14:04 PM
 */
public class TestNGDiffHyperLink extends DiffHyperlink implements Printable {

  public TestNGDiffHyperLink(final String expected, final String actual, final String filePath, final TestNGConsoleProperties consoleProperties) {
    super(expected, actual, filePath);
  }

  public void print(final ConsoleView printer) {
    printOn(new Printer() {
      public void print(final String text, final ConsoleViewContentType contentType) {
        printer.print(text, contentType);
      }

      public void printHyperlink(final String text, final HyperlinkInfo info) {
        printer.printHyperlink(text, info);
      }

      public void onNewAvaliable(final com.intellij.execution.junit2.Printable printable) {}
      public void mark() {}
    });
  }
}