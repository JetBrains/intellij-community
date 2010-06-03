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
package org.jetbrains.idea.maven.utils;

import com.intellij.codeInsight.AttachSourcesProvider;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.psi.PsiFile;
import gnu.trove.THashSet;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.*;

import javax.swing.*;
import java.util.*;

public class MavenAttachSourcesProvider implements AttachSourcesProvider {
  public Collection<AttachSourcesAction> getActions(final List<LibraryOrderEntry> orderEntries, final PsiFile psiFile) {
    Collection<MavenProject> projects = getMavenProjects(psiFile);
    if (projects.isEmpty()) return Collections.emptyList();
    if (findArtifacts(projects, orderEntries).isEmpty()) return Collections.emptyList();
                                      
    return Collections.<AttachSourcesAction>singleton(new AttachSourcesAction() {
      public String getName() {
        return ProjectBundle.message("maven.action.download.sources");
      }

      public String getBusyText() {
        return ProjectBundle.message("maven.action.download.sources.busy.text");
      }

      public ActionCallback perform(List<LibraryOrderEntry> orderEntries) {
        // may have been changed by this time...
        Collection<MavenProject> mavenProjects = getMavenProjects(psiFile);
        if (mavenProjects.isEmpty()) return new ActionCallback.Rejected();

        MavenProjectsManager manager = MavenProjectsManager.getInstance(psiFile.getProject());

        Collection<MavenArtifact> artifacts = findArtifacts(mavenProjects, orderEntries);
        if (artifacts.isEmpty()) return new ActionCallback.Rejected();

        final AsyncResult<MavenArtifactDownloader.DownloadResult> result = new AsyncResult<MavenArtifactDownloader.DownloadResult>();
        manager.scheduleArtifactsDownloading(mavenProjects, artifacts, true, false, result);

        final ActionCallback resultWrapper = new ActionCallback();

        result.doWhenDone(new AsyncResult.Handler<MavenArtifactDownloader.DownloadResult>() {
          public void run(MavenArtifactDownloader.DownloadResult downloadResult) {
            if (!downloadResult.unresolvedSources.isEmpty()) {
              String message = "<html>Sources not found for:";
              int count = 0;
              for (MavenId each : downloadResult.unresolvedSources) {
                if (count++ > 5) {
                  message += "<br>and more...";
                  break;
                }
                message += "<br>" + each.getDisplayString();
              }
              message += "</html>";

              final String finalMessage = message;
              SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                  Notifications.Bus.notify(new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP,
                                                            "Cannot download sources",
                                                            finalMessage,
                                                            NotificationType.WARNING),
                                           psiFile.getProject());
                }
              });
            }
            
            if (downloadResult.resolvedSources.isEmpty()) {
              resultWrapper.setRejected();
            }
            else {
              resultWrapper.setDone();
            }
          }
        });

        return resultWrapper;
      }
    });
  }

  private static Collection<MavenArtifact> findArtifacts(Collection<MavenProject> mavenProjects, List<LibraryOrderEntry> orderEntries) {
    Collection<MavenArtifact> artifacts = new THashSet<MavenArtifact>();
    for (MavenProject each : mavenProjects) {
      for (LibraryOrderEntry entry : orderEntries) {
        final MavenArtifact artifact = MavenRootModelAdapter.findArtifact(each, entry.getLibrary());
        if (artifact != null) artifacts.add(artifact);
      }
    }
    return artifacts;
  }

  private static Collection<MavenProject> getMavenProjects(PsiFile psiFile) {
    Project project = psiFile.getProject();
    Collection<MavenProject> result = new ArrayList<MavenProject>();
    for (OrderEntry each : ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(psiFile.getVirtualFile())) {
      MavenProject mavenProject = MavenProjectsManager.getInstance(project).findProject(each.getOwnerModule());
      if (mavenProject != null) result.add(mavenProject);
    }
    return result;
  }
}
