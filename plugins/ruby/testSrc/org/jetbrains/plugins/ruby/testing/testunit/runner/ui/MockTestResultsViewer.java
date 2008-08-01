package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.AbstractTestProxy;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitConsoleProperties;

import javax.swing.*;
import java.awt.*;

/**
 * @author Roman Chernyatchik
 */
public class MockTestResultsViewer implements TestResultsViewer {
  private RTestUnitConsoleProperties myProperties;
  private RTestUnitTestProxy myRootSuite;

  public MockTestResultsViewer(final RTestUnitConsoleProperties properties,
                               final RTestUnitTestProxy suite) {
    myProperties = properties;
    myRootSuite = suite;
  }

  public void addTab(final String name, final Component contentPane) {}

  public void addTestsProxySelectionListener(final TestProxyTreeSelectionListener listener) {}

  public void attachToProcess(final ProcessHandler processHandler) {}

  @Nullable
  public JComponent getContentPane() {
    return null;
  }

  public void initLogFilesManager() {}

  public RTestUnitTestProxy getTestsRootNode() {
    return myRootSuite;
  }

  public void selectAndNotify(@Nullable final AbstractTestProxy proxy) {}

  public void dispose() {
    myProperties.dispose();
  }
}
