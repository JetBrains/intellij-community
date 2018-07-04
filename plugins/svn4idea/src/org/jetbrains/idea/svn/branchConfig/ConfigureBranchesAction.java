// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import icons.SvnIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.history.SvnChangeList;

import static com.intellij.util.ArrayUtil.isEmpty;

public class ConfigureBranchesAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Presentation presentation = e.getPresentation();

    if (project == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    presentation.setText(SvnBundle.message("configure.branches.item"));
    presentation.setDescription(SvnBundle.message("configure.branches.item"));
    presentation.setIcon(SvnIcons.ConfigureBranches);

    presentation.setVisible(true);

    ChangeList[] changeLists = e.getData(VcsDataKeys.CHANGE_LISTS);
    presentation.setEnabled(!isEmpty(changeLists) &&
                            SvnVcs.getInstance(project).getName().equals(((CommittedChangeList)changeLists[0]).getVcs().getName()) &&
                            ((SvnChangeList)changeLists[0]).getRoot() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    ChangeList[] changeLists = e.getRequiredData(VcsDataKeys.CHANGE_LISTS);
    SvnChangeList svnList = (SvnChangeList)changeLists[0];

    BranchConfigurationDialog.configureBranches(project, svnList.getRoot());
  }
}