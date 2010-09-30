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

import com.intellij.ide.mavenService.MavenService;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableAttachHandler;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.SoutMavenConsole;
import org.jetbrains.idea.maven.facade.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.facade.MavenFacadeManager;
import org.jetbrains.idea.maven.importing.MavenExtraArtifactType;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.project.MavenEmbeddersManager;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.services.MavenServicesManager;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;

/**
 * @author Gregory.Shrago
 */
public class RepositoryAttachHandler implements LibraryTableAttachHandler {
  public String getLongName() {
    return "Attach Classes from Repository...";
  }

  public String getShortName() {
    return "Classes from Repository...";
  }

  public Icon getIcon() {
    return MavenIcons.MAVEN_ICON;
  }

  public ActionCallback performAttach(final Project project,
                                      final LibraryEditor libraryEditor,
                                      final @Nullable NullableComputable<Library.ModifiableModel> modelProvider) {

    MavenService.getInstance(project);
    final RepositoryAttachDialog dialog = new RepositoryAttachDialog(project, false);
    dialog.setTitle(getLongName());
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      final ActionCallback callback = new ActionCallback();
      final String copyTo = dialog.getDirectoryPath();
      final String coord = dialog.getCoordinateText();
      final boolean attachJavaDoc = dialog.getAttachJavaDoc();
      final boolean attachSources = dialog.getAttachSources();
      final SmartList<MavenExtraArtifactType> extraTypes = new SmartList<MavenExtraArtifactType>();
      if (attachSources) extraTypes.add(MavenExtraArtifactType.SOURCES);
      if (attachJavaDoc) extraTypes.add(MavenExtraArtifactType.DOCS);
      resolveLibrary(project, coord, extraTypes, dialog.getRepositories(), true, new Processor<List<MavenArtifact>>() {
        public boolean process(final List<MavenArtifact> artifacts) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              replaceLibraryData(project, libraryEditor, artifacts, copyTo);
              callback.setDone();
            }
          });
          final boolean nothingRetrieved = artifacts.isEmpty();
          final StringBuilder sb = new StringBuilder();
          if (nothingRetrieved) {
            sb.append("No files were downloaded for ").append(coord);
          }
          else {
            sb.append("The following files were downloaded:<br>");
            sb.append("<ol>");
            for (MavenArtifact each : artifacts) {
              sb.append("<li>");
              sb.append(each.getFile().getName());
              sb.append("</li>");
            }
            sb.append("</ol>");
          }
          final String title = "Attach Jars From Repository";
          if (nothingRetrieved && ModalityState.current().dominates(ModalityState.NON_MODAL)) {
            Messages.showErrorDialog(project, sb.toString(), title);
          }
          else {
            Notifications.Bus.notify(new Notification("Repository", sb.toString(), title,
                                                      nothingRetrieved ? NotificationType.WARNING : NotificationType.INFORMATION),
                                     NotificationDisplayType.STICKY_BALLOON, project);
          }
          return true;
        }
      });
      return callback;
    }
    return new ActionCallback.Rejected();
  }

  private static String getMavenCoordinate(String libraryName) {
    return libraryName.substring("Managed: ".length());
  }

  private static void replaceLibraryData(Project project,
                                         LibraryEditor libraryEditor,
                                         Collection<MavenArtifact> artifacts,
                                         String copyTo) {
    final String repoUrl = getLocalRepositoryUrl(project);
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      for (String url : libraryEditor.getUrls(type)) {
        if (url.startsWith(repoUrl)) {
          libraryEditor.removeRoot(url, type);
        }
      }
    }
    final VirtualFileManager manager = VirtualFileManager.getInstance();
    for (MavenArtifact each : artifacts) {
      try {
        File repoFile = each.getFile();
        File toFile = repoFile;
        if (copyTo != null) {
          toFile = new File(copyTo, repoFile.getName());
          if (repoFile.exists()) {
            FileUtil.copy(repoFile, toFile);
          }
        }
        String url = VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(toFile.getPath()));
        manager.refreshAndFindFileByUrl(url);
        if (MavenExtraArtifactType.DOCS.getDefaultClassifier().equals(each.getClassifier())) {
          libraryEditor.addRoot(url, JavadocOrderRootType.getInstance());
        }
        else if (MavenExtraArtifactType.SOURCES.getDefaultClassifier().equals(each.getClassifier())) {
          libraryEditor.addRoot(url, OrderRootType.SOURCES);
        }
        else {
          libraryEditor.addRoot(url, OrderRootType.CLASSES);
        }
      }
      catch (MalformedURLException e) {
        MavenLog.LOG.warn(e);
      }
      catch (IOException e) {
        MavenLog.LOG.warn(e);
      }
    }
  }

  private static String getLocalRepositoryUrl(Project project) {
    File file = MavenProjectsManager.getInstance(project).getGeneralSettings().getEffectiveLocalRepository();
    return VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(file.getPath()));
  }

  public static void searchArtifacts(final Project project, String coord,
                                     final PairProcessor<Collection<Pair<MavenArtifactInfo, MavenRepositoryInfo>>, Boolean> resultProcessor) {
    if (coord == null || coord.length() == 0) return;
    final MavenArtifactInfo template;
    if (coord.indexOf(':') == -1 && Character.isUpperCase(coord.charAt(0))) {
      template = new MavenArtifactInfo(null, null, null, "jar", null, coord, null);
    }
    else {
      template = new MavenArtifactInfo(getMavenId(coord), "jar", null);
    }
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Maven", false) {

      public void run(@NotNull ProgressIndicator indicator) {
        final Ref<List<Pair<MavenArtifactInfo, MavenRepositoryInfo>>> result
          = Ref.create(Collections.<Pair<MavenArtifactInfo, MavenRepositoryInfo>>emptyList());
        final Ref<Boolean> tooManyRef = Ref.create(Boolean.FALSE);
        try {
          MavenFacadeManager facade = MavenFacadeManager.getInstance();
          final List<Pair<MavenArtifactInfo, MavenRepositoryInfo>> resultList = new ArrayList<Pair<MavenArtifactInfo, MavenRepositoryInfo>>();
          for (String serviceUrl : MavenServicesManager.getServiceUrls()) {
            final List<MavenArtifactInfo> artifacts;
            try {
              artifacts = facade.findArtifacts(template, serviceUrl);
            }
            catch (Exception ex) {
              MavenLog.LOG.warn("Accessing Service at: " + serviceUrl, ex);
              continue;
            }
            if (!artifacts.isEmpty()) {
              final List<MavenRepositoryInfo> repositories = facade.getRepositories(serviceUrl);
              final HashMap<String, MavenRepositoryInfo> map = new HashMap<String, MavenRepositoryInfo>();
              for (MavenRepositoryInfo repository : repositories) {
                map.put(repository.getId(), repository);
              }
              for (MavenArtifactInfo artifact : artifacts) {
                if (artifact == null) {
                  tooManyRef.set(Boolean.TRUE);
                }
                else {
                  resultList.add(Pair.create(artifact, map.get(artifact.getRepositoryId())));
                }
              }
            }
          }
          result.set(resultList);
        }
        catch (Exception e) {
          handleError(null, e);
        }
        finally {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              resultProcessor.process(result.get(), tooManyRef.get());
            }
          });
        }
      }
    });
  }

  public static void searchRepositories(final Project project, final Collection<String> nexusUrls, final Processor<Collection<MavenRepositoryInfo>> resultProcessor) {
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Maven", false) {

      public void run(@NotNull ProgressIndicator indicator) {
        final Ref<List<MavenRepositoryInfo>> result = Ref.create(Collections.<MavenRepositoryInfo>emptyList());
        try {
          final MavenFacadeManager manager = MavenFacadeManager.getInstance();
          final ArrayList<MavenRepositoryInfo> repoList = new ArrayList<MavenRepositoryInfo>();
          for (String nexusUrl : nexusUrls) {
            final List<MavenRepositoryInfo> repositories;
            try {
              repositories = manager.getRepositories(nexusUrl);
            }
            catch (Exception ex) {
              MavenLog.LOG.warn("Accessing Service at: " + nexusUrl, ex);
              continue;
            }
            repoList.addAll(repositories);
          }
          result.set(repoList);
        }
        catch (Exception e) {
          handleError(null, e);
        }
        finally {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              resultProcessor.process(result.get());
            }
          });
        }
      }
    });
  }

  private static MavenId getMavenId(final String coord) {
    final String[] parts = coord.split(":");
    return new MavenId(parts.length > 0 ? parts[0] : null,
                                 parts.length > 1 ? parts[1] : null,
                                 parts.length > 2 ? parts[2] : null);

  }

  private static void handleError(String message, Exception e) {
    MavenLog.LOG.error(message, e);
  }

  public static void resolveLibrary(final Project project,
                                    final String coord,
                                    List<MavenExtraArtifactType> extraTypes, final Collection<MavenRepositoryInfo> repositories,
                                    boolean modal,
                                    final Processor<List<MavenArtifact>> resultProcessor) {
    final List<MavenArtifactInfo> artifacts = new ArrayList<MavenArtifactInfo>(3);
    final MavenId mavenId = getMavenId(coord);
    artifacts.add(new MavenArtifactInfo(mavenId, "jar", null));
    for (MavenExtraArtifactType extraType : extraTypes) {
      artifacts.add(new MavenArtifactInfo(mavenId, extraType.getDefaultExtension(), extraType.getDefaultClassifier()));
    }
    final Task task;
    if (modal) {
      task = new Task.Modal(project, "Maven", false) {
        public void run(@NotNull ProgressIndicator indicator) {
          doResolveInner(project, artifacts, repositories, resultProcessor, indicator);
        }
      };
    }
    else {
      task = new Task.Backgroundable(project, "Maven", false, PerformInBackgroundOption.DEAF) {
        public void run(@NotNull ProgressIndicator indicator) {
          doResolveInner(project, artifacts, repositories, resultProcessor, indicator);
        }

        @Override
        public boolean shouldStartInBackground() {
          return false;
        }
      };
    }

    ProgressManager.getInstance().run(task);
  }

  private static void doResolveInner(Project project,
                                     List<MavenArtifactInfo> artifacts,
                                     Collection<MavenRepositoryInfo> repositories,
                                     final Processor<List<MavenArtifact>> resultProcessor,
                                     ProgressIndicator indicator) {
    final Ref<List<MavenArtifact>> result = new Ref<List<MavenArtifact>>();

    MavenEmbeddersManager manager = MavenProjectsManager.getInstance(project).getEmbeddersManager();
    MavenEmbedderWrapper embedder = manager.getEmbedder(MavenEmbeddersManager.FOR_DOWNLOAD);
    try {
      embedder.customizeForResolve(new SoutMavenConsole(), new MavenProgressIndicator(indicator));
      List<MavenArtifact> resolved = embedder.resolveTransitively(artifacts,
                                                                  convertRepositories(repositories));
      result.set(ContainerUtil.findAll(resolved, new Condition<MavenArtifact>() {
        public boolean value(MavenArtifact mavenArtifact) {
          return mavenArtifact.isResolved();
        }
      }));
    }
    catch (MavenProcessCanceledException e) {
      return;
    }
    finally {
      manager.release(embedder);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          resultProcessor.process(result.get());
        }
      });
    }
  }

  private static List<MavenRemoteRepository> convertRepositories(Collection<MavenRepositoryInfo> infos) {
    List<MavenRemoteRepository> result = new ArrayList<MavenRemoteRepository>(infos.size());
    for (MavenRepositoryInfo each : infos) {
      result.add(new MavenRemoteRepository(each.getId(), each.getName(), each.getUrl(), null, null, null));
    }
    return result;
  }
}
