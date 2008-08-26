package org.jetbrains.plugins.ruby.testing.sm.runner.ui;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTestProxy;

import javax.swing.*;

/**
 * @author Roman Chernyatchik
 */
public class MockTestResultsViewer implements TestResultsViewer {
  private TestConsoleProperties myProperties;
  private SMTestProxy myRootSuite;

  public MockTestResultsViewer(final TestConsoleProperties properties,
                               final SMTestProxy suite) {
    myProperties = properties;
    myRootSuite = suite;
  }

  public void addTab(final String name, final Icon icon, final JComponent contentPane) {}

  public void addTestsProxySelectionListener(final TestProxyTreeSelectionListener listener) {}

  public void attachToProcess(final ProcessHandler processHandler) {}

  @Nullable
  public JComponent getContentPane() {
    return null;
  }

  public void initLogFilesManager() {}

  public SMTestProxy getTestsRootNode() {
    return myRootSuite;
  }

  public void selectAndNotify(@Nullable final AbstractTestProxy proxy) {}

  public void addEventsListener(final EventsListener listener) {}

  public void dispose() {
    myProperties.dispose();
  }
}
