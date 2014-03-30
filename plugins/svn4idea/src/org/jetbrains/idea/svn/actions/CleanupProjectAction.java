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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnVcs;

/**
 * @author yole
 */
public class CleanupProjectAction extends AnAction implements DumbAware {
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(SvnVcs.getInstance(project));
    new CleanupWorker(roots, project, "action.Subversion.cleanup.project.title").execute();
 }

  public void update(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    e.getPresentation().setVisible(isEnabled(project));
  }

  private static boolean isEnabled(final Project project) {
    if (project == null) return false;
    return ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(SvnVcs.VCS_NAME);
  }
}
