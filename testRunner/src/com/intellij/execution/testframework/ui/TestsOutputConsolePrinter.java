package com.intellij.execution.testframework.ui;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.testframework.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;

public class TestsOutputConsolePrinter implements Printer, Disposable {
  private final ConsoleView myConsole;
  private final TestConsoleProperties myProperties;
  private ChangingPrintable myCurrentPrintable = ChangingPrintable.DEAF;
  private Printer myOutput;
  private final DeferingPrinter myDeferingPrinter = new DeferingPrinter(false);
  private final Intermediate myOutputStorage = new DeferingPrinter(true);
  /**
   * It seems it is storage for uncaptured output by other printers (e.g. test proxies).
   * To prevent duplicated output collectioning output on this printer must be paused
   * (i.e. setCollectOutput(false)) after additional printer have been attached. You can
   * continue to collect output after additional printers will be deattached(e.g. test runner stops
   * sending events to test proxies).
   */
  private Intermediate myCurrentOutputStorage = myOutputStorage;
  private int myMarkOffset = 0;

  private final TestFrameworkPropertyListener<Boolean> myPropertyListener = new TestFrameworkPropertyListener<Boolean>() {
        public void onChanged(final Boolean value) {
          if (!value.booleanValue()) myMarkOffset = 0;
        }
      };

  public TestsOutputConsolePrinter(final ConsoleView console, final TestConsoleProperties properties) {
    myConsole = console;
    myProperties = properties;
    myProperties.addListener(TestConsoleProperties.SCROLL_TO_STACK_TRACE, myPropertyListener);
    myOutput = this;
  }

  public ConsoleView getConsole() {
    return myConsole;
  }

  public boolean isPaused() {
    return myOutput != this;
  }

  public void pause(final boolean doPause) {
    if (doPause)
      myOutput = myDeferingPrinter;
    else {
      myOutput = this;
      myDeferingPrinter.printOn(myOutput);
    }
  }

  public void print(final String text, final ConsoleViewContentType contentType) {
    myConsole.print(text, contentType);
  }

  public void onNewAvaliable(final Printable printable) {
    printable.printOn(myCurrentOutputStorage);
    printable.printOn(myOutput);
  }

  /**
   * Clears console, prints output of selected test and scrolls to beginning
   * of output.
   * This method must be invoked in Event Dispatch Thread
   * @param test Selected test
   */
  public void updateOnTestSelected(final PrintableTestProxy test) {
    if (myCurrentPrintable == test) {
      return;
    }
    myCurrentPrintable.setPrintLinstener(DEAF);
    myConsole.clear();
    myMarkOffset = 0;
    if (test == null) {
      myCurrentPrintable = ChangingPrintable.DEAF;
      return;
    }
    myCurrentPrintable = test;
    myCurrentPrintable.setPrintLinstener(this);
    if (test.isRoot()) {
      myOutputStorage.printOn(this);
    }
    myCurrentPrintable.printOn(this);
    scrollToBeginning();
  }

  public void printHyperlink(final String text, final HyperlinkInfo info) {
    myConsole.printHyperlink(text, info);
  }

  public void mark() {
    if (TestConsoleProperties.SCROLL_TO_STACK_TRACE.value(myProperties))
      myMarkOffset = myConsole.getContentSize();
  }

  public void dispose() {
    myProperties.removeListener(TestConsoleProperties.SCROLL_TO_STACK_TRACE, myPropertyListener);
  }
  
  public void setCollectOutput(final boolean doCollect) {
    myCurrentOutputStorage = doCollect ? myOutputStorage : DEAF;
  }

  public boolean canPause() {
    if (myCurrentPrintable instanceof AbstractTestProxy) {
      final AbstractTestProxy test = (AbstractTestProxy)myCurrentPrintable;
      return test.isInProgress();
    }
    return false;
  }

  protected void scrollToBeginning() {
    myConsole.performWhenNoDeferredOutput(new Runnable() {
      public void run() {
        myConsole.scrollTo(myMarkOffset);
      }
    });
  }
}