package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.Disposable;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

/**
 * @author Roman Chernyatchik
 */
public interface TestResultsViewer extends Disposable {
  /**
   * Add tab to viwer
   * @param name Tab name
   * @param contentPane Tab content pane
   */
  void addTab(final String name, final Component contentPane);

  /**
   * Subscribe on test proxy selection events
   * @param listener Listener
   */
  void addTestsProxySelectionListener(final TestProxyTreeSelectionListener listener);

  /**
   * On attach to process
   * @param processHandler Process handler
   */
  void attachToProcess(final ProcessHandler processHandler);

  /**
   * @return Content Pane of viewe
   */
  JComponent getContentPane();

  /**
   * Setups Log Files Manager and init log consoles
   */
  void initLogFilesManager();

  /**
   * On start testing, before tests and suits launching
   */
  void onStartTesting();

  /**
   * Fake Root for toplevel test suits/tests
   * @return root
   */
  RTestUnitTestProxy getTestsRootNode();

  /**
   * After test framework finish testing
   */
  void onFinishTesting();

  /**
   * Add test to viewer
   * @param testProxy Test
   * @param testsTotal Total amount of tests
   * @param testsCurrentCount Current test number
   */
  void onTestStarted(final RTestUnitTestProxy testProxy,
                   final int testsTotal, final int testsCurrentCount);

  /**
   * Adds suite to viewer
   * @param suite Suite
   */
  void onSuiteStarted(final RTestUnitTestProxy suite);

  /**
   * Update testing status
   * @param startTime Beginning of testing
   * @param endTime End time if finished, otherwise 0
   * @param testsTotal Total Amount of tests
   * @param testsCurrentCount Amount of run tests
   * @param failedTests Failed tests
   */
  void updateStatusLabel(final long startTime, final long endTime, final int testsTotal,
                         final int testsCurrentCount, final Set<AbstractTestProxy> failedTests);
}
