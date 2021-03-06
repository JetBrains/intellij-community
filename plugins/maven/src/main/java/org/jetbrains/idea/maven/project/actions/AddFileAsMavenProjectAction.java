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
package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.wizards.MavenOpenProjectProvider;

import static org.jetbrains.idea.maven.utils.actions.MavenActionUtil.*;

public class AddFileAsMavenProjectAction extends MavenAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {

    DataContext context = e.getDataContext();
    Project project = getProject(context);

    VirtualFile file = getSelectedFile(context);
    if (project != null && file != null) {
      MavenUtil.isProjectTrustedEnoughToImport(project, true);
      MavenOpenProjectProvider openProjectProvider = new MavenOpenProjectProvider();
      openProjectProvider.linkToExistingProject(file, project);
    }
  }

  @Override
  protected boolean isAvailable(@NotNull AnActionEvent e) {
    final DataContext context = e.getDataContext();
    VirtualFile file = getSelectedFile(context);
    return super.isAvailable(e)
           && isMavenProjectFile(file)
           && !isExistingProjectFile(context, file);
  }

  @Override
  protected boolean isVisible(@NotNull AnActionEvent e) {
    return super.isVisible(e) && isAvailable(e);
  }

  private static boolean isExistingProjectFile(DataContext context, VirtualFile file) {
    MavenProjectsManager manager = getProjectsManager(context);
    return manager != null && manager.findProject(file) != null;
  }

  @Nullable
  private static VirtualFile getSelectedFile(DataContext context) {
    return CommonDataKeys.VIRTUAL_FILE.getData(context);
  }
}
