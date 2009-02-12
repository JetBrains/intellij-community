package com.intellij.execution.testframework.sm.runner.ui.statistics;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
 */
public class ShowTestProxy extends AnAction{
  public void actionPerformed(final AnActionEvent e) {
    final StatisticsPanel sender = e.getData(StatisticsPanel.SM_TEST_RUNNER_STATISTICS);
    if (sender == null) {
      return;
    }

    sender.showSelectedProxyInTestsTree();
  }

  @Override
  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();

    // visible only in StatisticsTableView
    presentation.setVisible(e.getData(StatisticsPanel.SM_TEST_RUNNER_STATISTICS) != null);
    // enabled if some proxy is selected
    presentation.setEnabled(getSelectedTestProxy(e) != null);
  }

  @Nullable
  private Object getSelectedTestProxy(final AnActionEvent e) {
    return e.getDataContext().getData (AbstractTestProxy.DATA_CONSTANT);
  }}
