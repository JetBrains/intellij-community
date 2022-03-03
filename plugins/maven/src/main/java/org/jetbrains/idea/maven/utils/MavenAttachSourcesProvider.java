// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.idea.maven.importing.MavenExtraArtifactType;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenArtifactDownloader;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class MavenAttachSourcesProvider implements AttachSourcesProvider {
  @Override
  @NotNull
  public Collection<AttachSourcesAction> getActions(final List<LibraryOrderEntry> orderEntries, final PsiFile psiFile) {
    Collection<MavenProject> projects = getMavenProjects(psiFile);
    if (projects.isEmpty()) return Collections.emptyList();
    if (findArtifacts(projects, orderEntries).isEmpty()) return Collections.emptyList();

    return Collections.singleton(new AttachSourcesAction() {
      @Override
      public String getName() {
        return MavenProjectBundle.message("maven.action.download.sources");
      }

      @Override
      public String getBusyText() {
        return MavenProjectBundle.message("maven.action.download.sources.busy.text");
      }

      @Override
      public ActionCallback perform(List<LibraryOrderEntry> orderEntries) {
        // may have been changed by this time...
        Collection<MavenProject> mavenProjects = getMavenProjects(psiFile);
        if (mavenProjects.isEmpty()) {
          return ActionCallback.REJECTED;
        }

        MavenProjectsManager manager = MavenProjectsManager.getInstance(psiFile.getProject());

        Collection<MavenArtifact> artifacts = findArtifacts(mavenProjects, orderEntries);
        if (artifacts.isEmpty()) return ActionCallback.REJECTED;

        final AsyncPromise<MavenArtifactDownloader.DownloadResult> result = new AsyncPromise<>();
        manager.scheduleArtifactsDownloading(mavenProjects, artifacts, true, false, result);

        final ActionCallback resultWrapper = new ActionCallback();
        result.onSuccess(downloadResult -> {
          HtmlBuilder builder = null;
          if (!downloadResult.unresolvedSources.isEmpty()) {
            builder = new HtmlBuilder();
            builder.append(MavenProjectBundle.message("sources.not.found.for"));
            int count = 0;
            for (MavenId each : downloadResult.unresolvedSources) {
              if (count++ > 5) {
                builder.append(HtmlChunk.br()).append(MavenProjectBundle.message("and.more"));
                break;
              }
              builder.append(HtmlChunk.br()).append(each.getDisplayString());
            }
          }
          if (builder != null) {
            cleanUpUnresolvedSourceFiles(psiFile.getProject(), downloadResult.unresolvedSources);
            Notifications.Bus.notify(new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP,
                                                      MavenProjectBundle.message("maven.sources.cannot.download"),
                                                      builder.wrapWithHtmlBody().toString(),
                                                      NotificationType.WARNING),
                                     psiFile.getProject());
          }

          if (downloadResult.resolvedSources.isEmpty()) {
            resultWrapper.setRejected();
          }
          else {
            resultWrapper.setDone();
          }
        });
        return resultWrapper;
      }
    });
  }

  private static void cleanUpUnresolvedSourceFiles(Project project, Collection<MavenId> mavenIds) {
    for (MavenId mavenId : mavenIds) {
      Path parentFile = MavenUtil.getRepositoryParentFile(project, mavenId);
      if (parentFile == null) continue;
      try (Stream<Path> paths = Files.list(parentFile)) {
        paths
          .filter(path -> isTargetFile(path.getFileName().toString(), MavenExtraArtifactType.SOURCES))
          .forEach(path -> {
            try {
              FileUtil.delete(path);
            }
            catch (IOException e) {
              MavenLog.LOG.warn(path + " not deleted", e);
            }
          });
      }
      catch (IOException e) {
        MavenLog.LOG.warn(parentFile + " cannot be listed", e);
      }
    }
  }

  private static boolean isTargetFile(String name, MavenExtraArtifactType type) {
    return name.contains("-" + type.getDefaultClassifier()) && name.contains("." + type.getDefaultExtension());
  }

  private static Collection<MavenArtifact> findArtifacts(Collection<MavenProject> mavenProjects, List<LibraryOrderEntry> orderEntries) {
    Collection<MavenArtifact> artifacts = new HashSet<>();
    for (MavenProject each : mavenProjects) {
      for (LibraryOrderEntry entry : orderEntries) {
        final MavenArtifact artifact = MavenRootModelAdapter.findArtifact(each, entry.getLibrary());
        if (artifact != null && !"system".equals(artifact.getScope())) {
          artifacts.add(artifact);
        }
      }
    }
    return artifacts;
  }

  private static Collection<MavenProject> getMavenProjects(PsiFile psiFile) {
    Project project = psiFile.getProject();
    Collection<MavenProject> result = new ArrayList<>();
    for (OrderEntry each : ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(psiFile.getVirtualFile())) {
      MavenProject mavenProject = MavenProjectsManager.getInstance(project).findProject(each.getOwnerModule());
      if (mavenProject != null) result.add(mavenProject);
    }
    return result;
  }
}
