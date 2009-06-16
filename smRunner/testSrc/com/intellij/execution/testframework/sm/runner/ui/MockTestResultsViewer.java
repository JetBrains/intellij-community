package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.ui.tabs.JBTabs;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Roman Chernyatchik
 */
public class MockTestResultsViewer implements TestResultsViewer {
  private final TestConsoleProperties myProperties;
  private final SMTestProxy myRootSuite;

  public MockTestResultsViewer(final TestConsoleProperties properties,
                               final SMTestProxy suite) {
    myProperties = properties;
    myRootSuite = suite;
  }

  public void addTab(final String name, @Nullable final String tooltip, final Icon icon, final JComponent contentPane) {}

  @Nullable
  public JComponent getContentPane() {
    return null;
  }

  public SMTestProxy getTestsRootNode() {
    return myRootSuite;
  }

  public void selectAndNotify(@Nullable final AbstractTestProxy proxy) {}

  public void addEventsListener(final EventsListener listener) {}

  public JBTabs getTabs() { return null; }


  public void dispose() {
    myProperties.dispose();
  }

  public void setShowStatisticForProxyHandler(final PropagateSelectionHandler handler) {}

  public void showStatisticsForSelectedProxy() {}
}
