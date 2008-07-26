package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.testframework.Printable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.ui.ConsoleViewContentType;

public class MockPrinter implements Printer {
  private boolean myShouldReset = false;
  private boolean myHasPrinted = false;
  private StringBuilder myStdOut = new StringBuilder();
  private StringBuilder myStdErr = new StringBuilder();
  private StringBuilder myStdSys = new StringBuilder();

  public MockPrinter() {
    this(true);
  }

  public MockPrinter(boolean shouldReset) {
    myShouldReset = shouldReset;
  }

  public void print(String s, ConsoleViewContentType contentType) {
    myHasPrinted = true;
    if (contentType == ConsoleViewContentType.NORMAL_OUTPUT) {
      myStdOut.append(s);
    }
    else if (contentType == ConsoleViewContentType.ERROR_OUTPUT) {
      myStdErr.append(s);
    }
    else if (contentType == ConsoleViewContentType.SYSTEM_OUTPUT) {
      myStdSys.append(s);
    }
  }

  public String getStdOut() {
    return myStdOut.toString();
  }

  public String getStdErr() {
    return myStdErr.toString();
  }

  public String getStdSys() {
    return myStdSys.toString();
  }

  public void setHasPrinted(final boolean hasPrinted) {
    myHasPrinted = hasPrinted;
  }

  public boolean isShouldReset() {
    return myShouldReset;
  }

  public void resetIfNecessary() {
    if (isShouldReset()) {
      myStdErr.setLength(0);
      myStdOut.setLength(0);
      myStdSys.setLength(0);
    }
    setHasPrinted(false);
  }

  public boolean hasPrinted() {
    return myHasPrinted;
  }

  public void onNewAvaliable(Printable printable) {
    printable.printOn(this);
  }

  public void printHyperlink(String text, HyperlinkInfo info) {
  }

  public void mark() {}
}
