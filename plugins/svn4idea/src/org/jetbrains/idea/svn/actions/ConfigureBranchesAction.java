/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.BranchConfigurationDialog;
import org.jetbrains.idea.svn.history.SvnChangeList;

public class ConfigureBranchesAction extends AnAction implements DumbAware {
  public void update(final AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    final Presentation presentation = e.getPresentation();

    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    presentation.setText(SvnBundle.message("configure.branches.item"));
    presentation.setDescription(SvnBundle.message("configure.branches.item"));
    presentation.setIcon(IconLoader.getIcon("/icons/ConfigureBranches.png"));

    presentation.setVisible(true);
    
    final ChangeList[] cls = e.getData(VcsDataKeys.CHANGE_LISTS);
    presentation.setEnabled((cls != null) && (cls.length > 0) &&
                            (SvnVcs.getInstance(project).getName().equals(((CommittedChangeList) cls[0]).getVcs().getName())) &&
                            (((SvnChangeList) cls[0]).getRoot() != null));
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    final ChangeList[] cls = e.getData(VcsDataKeys.CHANGE_LISTS);
    if ((cls == null) || (cls.length == 0) ||
        (! SvnVcs.getInstance(project).getName().equals(((CommittedChangeList) cls[0]).getVcs().getName())) ||
        (((SvnChangeList) cls[0]).getRoot() == null)) {
      return;
    }
    final SvnChangeList svnList = (SvnChangeList) cls[0];
    BranchConfigurationDialog.configureBranches(project, svnList.getRoot(), true);
  }
}
