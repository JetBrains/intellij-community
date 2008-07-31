package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.Disposable;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;

import javax.swing.*;
import java.awt.*;

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
   * Fake Root for toplevel test suits/tests
   * @return root
   */
  RTestUnitTestProxy getTestsRootNode();
}
