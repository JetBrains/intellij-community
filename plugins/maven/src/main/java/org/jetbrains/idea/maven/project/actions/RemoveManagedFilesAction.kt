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

import com.intellij.CommonBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.util.List;

import static org.jetbrains.idea.maven.utils.actions.MavenActionUtil.getProject;

/**
 * Unlink Maven Projects
 */
public class RemoveManagedFilesAction extends MavenAction {
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

      MavenProject aggregator = projectsManager.findAggregator(mavenProject);
      while (aggregator != null && !projectsManager.isManagedFile(aggregator.getFile())) {
        aggregator = projectsManager.findAggregator(aggregator);
      }

      if (aggregator != null && !selectedFiles.contains(aggregator.getFile())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext context = e.getDataContext();

    final Project project = getProject(context);
    if (project == null) {
      return;
    }
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);

    List<VirtualFile> selectedFiles = MavenActionUtil.getMavenProjectsFiles(context);
    projectsManager.removeManagedFiles(selectedFiles, (mavenProject) -> {
      assert mavenProject != null;

      MavenProject aggregator = projectsManager.findAggregator(mavenProject);
      while (aggregator != null && !projectsManager.isManagedFile(aggregator.getFile())) {
        aggregator = projectsManager.findAggregator(aggregator);
      }

      if (aggregator != null && !selectedFiles.contains(aggregator.getFile())) {
        notifyUser(context, mavenProject, aggregator);
      }
    }, (names) -> {
      int returnCode =
        Messages
          .showOkCancelDialog(ExternalSystemBundle.message("action.detach.external.confirmation.prompt", "Maven", names.size(), names),
                              getActionTitle(names),
                              CommonBundle.message("button.remove"), CommonBundle.getCancelButtonText(),
                              Messages.getQuestionIcon());
      if (returnCode != Messages.OK) {
        return false;
      }
      return true;
    });
  }

  @Nls
  private static String getActionTitle(List<String> names) {
    return StringUtil.pluralize(ExternalSystemBundle.message("action.detach.external.project.text", "Maven"), names.size());
  }

  private static void notifyUser(DataContext context, MavenProject mavenProject, MavenProject aggregator) {
    String aggregatorDescription = " (" + aggregator.getMavenId().getDisplayString() + ')';
    Notification notification =
      new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, MavenProjectBundle.message("maven.module.remove.failed"),
                       MavenProjectBundle
                         .message("maven.module.remove.failed.explanation", mavenProject.getDisplayName(), aggregatorDescription),
                       NotificationType.ERROR
      );

    notification.setImportant(true);
    notification.notify(getProject(context));
  }
}