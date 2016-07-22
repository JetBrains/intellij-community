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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.util.ArrayList;
import java.util.List;

public class RemoveManagedFilesAction extends MavenAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    if (!super.isAvailable(e)) return false;
    return MavenActionUtil.getMavenProjectsFiles(e.getDataContext()).size() > 0;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext context = e.getDataContext();

    MavenProjectsManager projectsManager = MavenActionUtil.getProjectsManager(context);
    if(projectsManager == null) return;

    List<VirtualFile> selectedFiles = MavenActionUtil.getMavenProjectsFiles(context);
    List<VirtualFile> removableFiles = new ArrayList<>();

    for (VirtualFile pomXml : selectedFiles) {
      if (projectsManager.isManagedFile(pomXml)) {
        removableFiles.add(pomXml);
      }
      else {
        notifyUserIfNeeded(context, projectsManager, selectedFiles, pomXml);
      }
    }
    projectsManager.removeManagedFiles(removableFiles);
  }

  private static void notifyUserIfNeeded(DataContext context,
                                         MavenProjectsManager projectsManager,
                                         List<VirtualFile> selectedFiles,
                                         VirtualFile pomXml) {
    MavenProject mavenProject = projectsManager.findProject(pomXml);
    assert mavenProject != null;

    MavenProject aggregator = projectsManager.findAggregator(mavenProject);
    while (aggregator != null && !projectsManager.isManagedFile(aggregator.getFile())) {
      aggregator = projectsManager.findAggregator(aggregator);
    }

    if (aggregator != null && !selectedFiles.contains(aggregator.getFile())) {
      notifyUser(context, mavenProject, aggregator);
    }
  }

  private static void notifyUser(DataContext context, MavenProject mavenProject, MavenProject aggregator) {
    String aggregatorDescription = " (" + aggregator.getMavenId().getDisplayString() + ')';
    Notification notification = new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "Failed to remove project",
                                                 "You can not remove " + mavenProject.getName() + " because it's " +
                                                 "imported as a module of another project" +
                                                 aggregatorDescription
                                                 + ". You can use Ignore action. Only root project can be removed.",
                                                 NotificationType.ERROR
    );

    notification.setImportant(true);
    notification.notify(MavenActionUtil.getProject(context));
  }
}