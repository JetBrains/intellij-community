package org.jetbrains.plugins.ruby.testing.sm.runner.ui;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.Disposable;
import com.intellij.ui.tabs.JBTabs;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTestProxy;

import javax.swing.*;

/**
 * @author Roman Chernyatchik
 */
public interface TestResultsViewer extends Disposable {
  /**
   * Add tab to viwer
   * @param name Tab name
   * @param tooltip
   * @param icon
   * @param contentPane Tab content pane
   */
  void addTab(final String name, @Nullable final String tooltip, @Nullable final Icon icon, final JComponent contentPane);

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
  SMTestProxy getTestsRootNode();

  /**
   * Selects test or suite in Tests tree and notify about selection changed
   * @param proxy
   */
  void selectAndNotify(@Nullable final AbstractTestProxy proxy);

  void addEventsListener(final EventsListener listener);

  JBTabs getTabs();

  interface EventsListener {
    void onTestNodeAdded(final TestResultsViewer sender, final SMTestProxy test);
    void onTestingFinished(final TestResultsViewer sender);
  }
}
