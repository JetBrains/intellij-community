/*
 * User: anna
 * Date: 26-Mar-2008
 */
package com.intellij.refactoring.typeMigration.ui;

import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.ui.UsagesPanel;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfoToUsageConverter;

public class MigrationConflictsPanel extends UsagesPanel{
  public MigrationConflictsPanel(Project project) {
    super(project);
  }

  public String getInitialPositionText() {
    return "No migration conflicts found";
  }

  public String getCodeUsagesString() {
    return "Found migration conflicts";
  }

  @Override
  public void showUsages(final UsageInfoToUsageConverter.TargetElementsDescriptor descriptor, final UsageInfo[] usageInfos) {
    super.showUsages(descriptor, usageInfos);
  }
}