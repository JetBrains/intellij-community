package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.Disposable;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import org.jetbrains.annotations.Nullable;

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
  void addTab(final String name, @Nullable String tooltip, @Nullable Icon icon, JComponent contentPane);

  /**
   * Subscribe on test proxy selection events
   * @param listener Listener
   */
  void addTestsProxySelectionListener(TestProxyTreeSelectionListener listener);

  /**
   * On attach to process
   * @param processHandler Process handler
   */
  void attachToProcess(ProcessHandler processHandler);

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
  void selectAndNotify(@Nullable AbstractTestProxy proxy);

  void addEventsListener(EventsListener listener);

  JBTabs getTabs();

  void setShowStatisticForProxyHandler(TestProxySelectionChangedListener handler);

  /**
   * If handler for statistics was set this method will execute it
   */
  void showStatisticsForSelectedProxy();

  interface EventsListener {
    void onTestNodeAdded(TestResultsViewer sender, SMTestProxy test);
    void onTestingFinished(TestResultsViewer sender);
  }
}
