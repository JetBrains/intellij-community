// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.history.SvnChangeList;

import static com.intellij.util.ArrayUtil.isEmpty;
import static org.jetbrains.idea.svn.SvnBundle.messagePointer;

public class ConfigureBranchesAction extends DumbAwareAction {
  public ConfigureBranchesAction() {
    super(messagePointer("action.Subversion.ConfigureBranches.text"));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Presentation presentation = e.getPresentation();

    if (project == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }

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