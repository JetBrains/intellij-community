package com.intellij.execution.testframework.ui;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.testframework.ExternalOutput;
import com.intellij.execution.testframework.HyperLink;
import com.intellij.execution.testframework.Printable;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class BaseTestsOutputConsoleView implements ConsoleView, ObservableConsoleView {
  private final ConsoleView myConsole;
  private TestsOutputConsolePrinter myPrinter;
  private TestConsoleProperties myProperties;

  public BaseTestsOutputConsoleView(final TestConsoleProperties properties) {
    myProperties = properties;
    myConsole = TextConsoleBuilderFactory.getInstance().createBuilder(properties.getProject()).getConsole();
    myPrinter = new TestsOutputConsolePrinter(myConsole, properties);
  }

  public void print(final String s, final ConsoleViewContentType contentType) {
    printNew(new ExternalOutput(s, contentType));
  }

  public void clear() {
    myConsole.clear();
  }

  public void scrollTo(final int offset) {
    myConsole.scrollTo(offset);
  }

  public void setOutputPaused(final boolean value) {
    if (myPrinter != null) {
      myPrinter.pause(value);
    }
  }

  public boolean isOutputPaused() {
    //noinspection SimplifiableConditionalExpression
    return myPrinter == null ? true : myPrinter.isPaused();
  }

  public boolean hasDeferredOutput() {
    return myConsole.hasDeferredOutput();
  }

  public void performWhenNoDeferredOutput(final Runnable runnable) {
    myConsole.performWhenNoDeferredOutput(runnable);
  }

  public void setHelpId(final String helpId) {
    myConsole.setHelpId(helpId);
  }

  public void addMessageFilter(final Filter filter) {
    myConsole.addMessageFilter(filter);
  }

  public void printHyperlink(final String hyperlinkText, final HyperlinkInfo info) {
    printNew(new HyperLink(hyperlinkText, info));
  }

  public int getContentSize() {
    return myConsole.getContentSize();
  }

  public boolean canPause() {
    return myPrinter != null && myPrinter.canPause() && myConsole.canPause();
  }

  public JComponent getComponent() {
    return myConsole.getComponent();
  }

  public JComponent getPreferredFocusableComponent() {
    return myConsole.getPreferredFocusableComponent();
  }

  public void dispose() {
    Disposer.dispose(myConsole);
    myPrinter = null;

    myProperties.dispose();
    myProperties = null;
  }

  public void addChangeListener(final ChangeListener listener, final Disposable parent) {
    if (myConsole instanceof ObservableConsoleView) {
      ((ObservableConsoleView)myConsole).addChangeListener(listener, parent);
    } else {
      throw new UnsupportedOperationException(myConsole.getClass().getName());
    }
  }

  @NotNull
  public AnAction[] createUpDownStacktraceActions() {
    return getConsole().createUpDownStacktraceActions();
  }

  protected ConsoleView getConsole() {
    return myConsole;
  }

  protected TestsOutputConsolePrinter getPrinter() {
    return myPrinter;
  }

  private void printNew(final Printable printable) {
    if (myPrinter != null) {
      myPrinter.onNewAvaliable(printable);
    }
  }
}
