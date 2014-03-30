/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.WorkingCopiesContent;

public class MergeFromAction extends DumbAwareAction {
  public MergeFromAction() {
    super("Merge from...", "One-click merge for feature branches", null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (! isEnabled(e)) return;
    final DataContext dc = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dc);
    if (project == null) return;

    WorkingCopiesContent.show(project);
  }

  @Override
  public void update(AnActionEvent e) {
    final DataContext dc = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dc);
    if (project == null || project.isDefault()) return;
    e.getPresentation().setVisible(isEnabled(e));
  }

  private boolean isEnabled(AnActionEvent e) {
    final DataContext dc = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dc);
    if (project == null || project.isDefault()) return false;
    final VirtualFile[] files = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(SvnVcs.getInstance(project));
    return files != null && files.length > 0;
  }
}
