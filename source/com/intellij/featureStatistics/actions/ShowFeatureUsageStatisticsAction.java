package com.intellij.featureStatistics.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;

public class ShowFeatureUsageStatisticsAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    new ShowFeatureUsageStatisticsDialog(getProject(e)).show();
  }

  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(getProject(e) != null);
  }

  private Project getProject(AnActionEvent e) {
    return (Project)e.getDataContext().getData(DataConstants.PROJECT);
  }
}