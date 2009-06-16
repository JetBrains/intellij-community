package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.Disposable;
import com.intellij.ui.tabs.JBTabs;
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
   * @return Content Pane of viewe
   */
  JComponent getContentPane();

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

  void setShowStatisticForProxyHandler(PropagateSelectionHandler handler);

  /**
   * If handler for statistics was set this method will execute it
   */
  void showStatisticsForSelectedProxy();

  interface EventsListener extends TestProxyTreeSelectionListener {
    void onTestNodeAdded(TestResultsViewer sender, SMTestProxy test);
    void onTestingFinished(TestResultsViewer sender);
  }
}
