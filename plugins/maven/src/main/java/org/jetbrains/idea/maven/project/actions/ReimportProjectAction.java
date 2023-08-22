// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.util.List;

import static org.jetbrains.idea.maven.utils.actions.MavenActionUtil.getProject;

/**
 * Reload project
 */
public class ReimportProjectAction extends MavenProjectsAction {
  @Override
  protected boolean isVisible(@NotNull AnActionEvent e) {
    if (!super.isVisible(e)) return false;
    final DataContext context = e.getDataContext();

    final Project project = getProject(context);
    if (project == null) return false;

    List<VirtualFile> selectedFiles = MavenActionUtil.getMavenProjectsFiles(context);
    if (selectedFiles.size() == 0) return false;
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
    for (VirtualFile pomXml : selectedFiles) {
      MavenProject mavenProject = projectsManager.findProject(pomXml);
      if (mavenProject == null) return false;
      if (projectsManager.isIgnored(mavenProject)) return false;
    }
    return true;
  }

  @Override
  protected void perform(@NotNull MavenProjectsManager manager, List<MavenProject> mavenProjects, AnActionEvent e) {
    if (MavenUtil.isProjectTrustedEnoughToImport(manager.getProject())) {
      FileDocumentManager.getInstance().saveAllDocuments();
      manager.forceUpdateProjects(mavenProjects);
    }
  }
}