/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils.library;

import com.intellij.CommonBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.SoutMavenConsole;
import org.jetbrains.idea.maven.importing.MavenExtraArtifactType;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.project.MavenEmbeddersManager;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.services.MavenRepositoryServicesManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.RepositoryAttachDialog;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Gregory.Shrago
 */
public class RepositoryAttachHandler {

  @Nullable
  public static NewLibraryConfiguration chooseLibraryAndDownload(final @NotNull Project project,
                                                                 final @Nullable String initialFilter,
                                                                 JComponent parentComponent) {
    RepositoryAttachDialog dialog = new RepositoryAttachDialog(project, initialFilter);
    dialog.setTitle("Download Library From Maven Repository");
    dialog.show();
    if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return null;
    }

    String copyTo = dialog.getDirectoryPath();
    String coord = dialog.getCoordinateText();
    boolean attachJavaDoc = dialog.getAttachJavaDoc();
    boolean attachSources = dialog.getAttachSources();
    List<MavenRepositoryInfo> repositories = dialog.getRepositories();
    NewLibraryConfiguration configuration = resolveAndDownload(project, coord, attachJavaDoc, attachSources, copyTo, repositories);
    if (configuration == null) {
      Messages.showErrorDialog(parentComponent, "No files were downloaded for " + coord, CommonBundle.getErrorTitle());
    }
    return configuration;
  }

  public static List<MavenArtifact> resolveAndDownload(final Project project, final String coord, List<MavenRepositoryInfo> repositories) {
    final SmartList<MavenExtraArtifactType> extraTypes = new SmartList<MavenExtraArtifactType>();
    final Ref<List<MavenArtifact>> result = Ref.create(null);
    resolveLibrary(project, coord, extraTypes, repositories, new Processor<List<MavenArtifact>>() {
      public boolean process(final List<MavenArtifact> artifacts) {
        result.set(artifacts);

        if (!artifacts.isEmpty()) {
          notifyArtifactsDownloaded(project, artifacts);
        }
        return true;
      }
    });
    return result.get();
  }

  public static NewLibraryConfiguration resolveAndDownload(final Project project,
                                                           final String coord,
                                                           boolean attachJavaDoc,
                                                           boolean attachSources,
                                                           @Nullable final String copyTo,
                                                           List<MavenRepositoryInfo> repositories) {
    final List<OrderRoot> roots = resolveAndDownloadImpl(project, coord, attachJavaDoc, attachSources, copyTo, repositories);
    return new NewLibraryConfiguration(coord, RepositoryLibraryType.getInstance(), new RepositoryLibraryProperties(coord)) {
      @Override
      public void addRoots(@NotNull LibraryEditor editor) {
        editor.addRoots(roots);
      }
    };
  }

  public static List<OrderRoot> resolveAndDownloadImpl(final Project project,
                                                       final String coord,
                                                       boolean attachJavaDoc,
                                                       boolean attachSources,
                                                       @Nullable final String copyTo,
                                                       List<MavenRepositoryInfo> repositories) {
    final SmartList<MavenExtraArtifactType> extraTypes = new SmartList<MavenExtraArtifactType>();
    if (attachSources) extraTypes.add(MavenExtraArtifactType.SOURCES);
    if (attachJavaDoc) extraTypes.add(MavenExtraArtifactType.DOCS);
    final Ref<List<OrderRoot>> result = Ref.create(null);
    resolveLibrary(project, coord, extraTypes, repositories, new Processor<List<MavenArtifact>>() {
      public boolean process(final List<MavenArtifact> artifacts) {
        if (!artifacts.isEmpty()) {
          AccessToken accessToken = WriteAction.start();
          try {
            final List<OrderRoot> roots = createRoots(artifacts, copyTo);
            result.set(roots);
          }
          finally {
            accessToken.finish();
          }
          notifyArtifactsDownloaded(project, artifacts);
        }
        return true;
      }
    });
    return result.get();
  }

  public static void notifyArtifactsDownloaded(Project project, List<MavenArtifact> artifacts) {
    final StringBuilder sb = new StringBuilder();
    final String title = "The following files were downloaded:";
    sb.append("<ol>");
    for (MavenArtifact each : artifacts) {
      sb.append("<li>");
      sb.append(each.getFile().getName());
      final String scope = each.getScope();
      if (scope != null) {
        sb.append(" (");
        sb.append(scope);
        sb.append(")");
      }
      sb.append("</li>");
    }
    sb.append("</ol>");
    Notifications.Bus.notify(new Notification("Repository", title, sb.toString(), NotificationType.INFORMATION), project);
  }

  public static List<OrderRoot> createRoots(@NotNull Collection<MavenArtifact> artifacts, @Nullable String copyTo) {
    final List<OrderRoot> result = new ArrayList<OrderRoot>();
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
        // search for jar file first otherwise lib root won't be found!
        manager.refreshAndFindFileByUrl(VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(toFile.getPath())));
        final String url = VfsUtil.getUrlForLibraryRoot(toFile);
        final VirtualFile file = manager.refreshAndFindFileByUrl(url);
        if (file != null) {
          OrderRootType rootType;
          if (MavenExtraArtifactType.DOCS.getDefaultClassifier().equals(each.getClassifier())) {
            rootType = JavadocOrderRootType.getInstance();
          }
          else if (MavenExtraArtifactType.SOURCES.getDefaultClassifier().equals(each.getClassifier())) {
            rootType = OrderRootType.SOURCES;
          }
          else {
            rootType = OrderRootType.CLASSES;
          }
          result.add(new OrderRoot(file, rootType));
        }
      }
      catch (MalformedURLException e) {
        MavenLog.LOG.warn(e);
      }
      catch (IOException e) {
        MavenLog.LOG.warn(e);
      }
    }
    return result;
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
        String[] urls = MavenRepositoryServicesManager.getServiceUrls();
        boolean tooManyResults = false;
        final AtomicBoolean proceedFlag = new AtomicBoolean(true);

        for (int i = 0, length = urls.length; i < length; i++) {
          if (!proceedFlag.get()) break;
          final List<Pair<MavenArtifactInfo, MavenRepositoryInfo>> resultList = new ArrayList<Pair<MavenArtifactInfo, MavenRepositoryInfo>>();
          try {
            String serviceUrl = urls[i];
            final List<MavenArtifactInfo> artifacts;
            artifacts = MavenRepositoryServicesManager.findArtifacts(template, serviceUrl);
            if (!artifacts.isEmpty()) {
              if (!proceedFlag.get()) {
                break;
              }

              List<MavenRepositoryInfo> repositories = MavenRepositoryServicesManager.getRepositories(serviceUrl);
              Map<String, MavenRepositoryInfo> map = new THashMap<String, MavenRepositoryInfo>();
              for (MavenRepositoryInfo repository : repositories) {
                map.put(repository.getId(), repository);
              }
              for (MavenArtifactInfo artifact : artifacts) {
                if (artifact == null) {
                  tooManyResults = true;
                }
                else {
                  MavenRepositoryInfo repository = map.get(artifact.getRepositoryId());
                  // if the artifact is provided by an unsupported repository just skip it
                  // because it won't be resolved anyway
                  if (repository == null) continue;
                  resultList.add(Pair.create(artifact, repository));
                }
              }
            }
          }
          catch (Exception e) {
            MavenLog.LOG.error(e);
          }
          finally {
            if (!proceedFlag.get()) break;
            final Boolean aBoolean = i == length - 1 ? tooManyResults : null;
            ApplicationManager.getApplication().invokeLater(
              new Runnable() {
                public void run() {
                  proceedFlag.set(resultProcessor.process(resultList, aBoolean));
                }
              }, new Condition() {
                @Override
                public boolean value(Object o) {
                  return !proceedFlag.get();
                }
              });
          }
        }
      }
    });
  }

  public static void searchRepositories(final Project project,
                                        final Collection<String> nexusUrls,
                                        final Processor<Collection<MavenRepositoryInfo>> resultProcessor) {
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Maven", false) {

      public void run(@NotNull ProgressIndicator indicator) {
        final Ref<List<MavenRepositoryInfo>> result = Ref.create(Collections.<MavenRepositoryInfo>emptyList());
        try {
          final ArrayList<MavenRepositoryInfo> repoList = new ArrayList<MavenRepositoryInfo>();
          for (String nexusUrl : nexusUrls) {
            final List<MavenRepositoryInfo> repositories;
            try {
              repositories = MavenRepositoryServicesManager.getRepositories(nexusUrl);
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
          MavenLog.LOG.error(e);
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

  private static void resolveLibrary(final Project project,
                                     final String coord,
                                     final List<MavenExtraArtifactType> extraTypes,
                                     final Collection<MavenRepositoryInfo> repositories,
                                     final Processor<List<MavenArtifact>> resultProcessor) {
    final MavenId mavenId = getMavenId(coord);
    final Task task = new Task.Modal(project, "Maven", false) {
      public void run(@NotNull ProgressIndicator indicator) {
        doResolveInner(project, mavenId, extraTypes, repositories, resultProcessor, indicator);
      }
    };
    ProgressManager.getInstance().run(task);
  }

  private static void doResolveInner(Project project,
                                     MavenId mavenId,
                                     List<MavenExtraArtifactType> extraTypes,
                                     Collection<MavenRepositoryInfo> repositories,
                                     final Processor<List<MavenArtifact>> resultProcessor,
                                     ProgressIndicator indicator) {
    doResolveInner(project, Collections.singletonList(mavenId), extraTypes, repositories, resultProcessor, indicator);
  }

  public static List<String> retrieveVersions(@NotNull final Project project,
                                              @NotNull final String groupId,
                                              @NotNull final String artifactId,
                                              @NotNull final String remoteRepository) {
    MavenEmbeddersManager manager = MavenProjectsManager.getInstance(project).getEmbeddersManager();
    MavenEmbedderWrapper embedder = manager.getEmbedder(MavenEmbeddersManager.FOR_GET_VERSIONS);
    embedder.customizeForGetVersions();
    try {
      List<String> versions = embedder.retrieveVersions(groupId, artifactId, remoteRepository);
      Collections.sort(versions, new Comparator<String>() {
        @Override
        public int compare(String o1, String o2) {
          return StringUtil.compareVersionNumbers(o2, o1);
        }
      });
      return versions;
    }
    catch (MavenProcessCanceledException e) {
      return Collections.emptyList();
    }
    finally {
      manager.release(embedder);
    }
  }

  public static void doResolveInner(Project project,
                                    List<MavenId> mavenIds,
                                    List<MavenExtraArtifactType> extraTypes,
                                    Collection<MavenRepositoryInfo> repositories,
                                    final Processor<List<MavenArtifact>> resultProcessor,
                                    ProgressIndicator indicator) {
    boolean cancelled = false;
    final Collection<MavenArtifact> result = new LinkedHashSet<MavenArtifact>();
    MavenEmbeddersManager manager = MavenProjectsManager.getInstance(project).getEmbeddersManager();
    MavenEmbedderWrapper embedder = manager.getEmbedder(MavenEmbeddersManager.FOR_DOWNLOAD);
    try {
      final MavenGeneralSettings mavenGeneralSettings = MavenProjectsManager.getInstance(project).getGeneralSettings();
      embedder.customizeForResolve(
        new SoutMavenConsole(mavenGeneralSettings.getOutputLevel(), mavenGeneralSettings.isPrintErrorStackTraces()),
        new MavenProgressIndicator(indicator));
      List<MavenRemoteRepository> remoteRepositories = convertRepositories(repositories);
      List<MavenArtifactInfo> artifacts = new ArrayList<MavenArtifactInfo>(mavenIds.size());
      for (MavenId id : mavenIds) {
        artifacts.add(new MavenArtifactInfo(id, "jar", null));
      }
      List<MavenArtifact> firstResult = embedder.resolveTransitively(artifacts, remoteRepositories);
      for (MavenArtifact artifact : firstResult) {
        if (!artifact.isResolved() || MavenConstants.SCOPE_TEST.equals(artifact.getScope())) {
          continue;
        }
        result.add(artifact);
      }
      // download docs & sources
      if (!extraTypes.isEmpty()) {
        Set<String> allowedClassifiers = new THashSet<String>();
        Collection<MavenArtifactInfo> resolve = new LinkedHashSet<MavenArtifactInfo>();
        for (MavenExtraArtifactType extraType : extraTypes) {
          allowedClassifiers.add(extraType.getDefaultClassifier());
          for (MavenId id : mavenIds) {
            resolve.add(new MavenArtifactInfo(id, extraType.getDefaultExtension(), extraType.getDefaultClassifier()));
          }
          // skip sources/javadoc for dependencies
        }
        for (MavenArtifact artifact : embedder.resolveTransitively(new ArrayList<MavenArtifactInfo>(resolve), remoteRepositories)) {
          if (!artifact.isResolved() || MavenConstants.SCOPE_TEST.equals(artifact.getScope()) || !allowedClassifiers.contains(artifact.getClassifier())) {
            continue;
          }
          result.add(artifact);
        }
      }
    }
    catch (MavenProcessCanceledException e) {
      cancelled = true;
    }
    finally {
      manager.release(embedder);
      if (!cancelled) {
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          public void run() {
            resultProcessor.process(new ArrayList<MavenArtifact>(result));
          }
        }, indicator.getModalityState());
      }
    }
  }

  private static List<MavenRemoteRepository> convertRepositories(Collection<MavenRepositoryInfo> infos) {
    List<MavenRemoteRepository> result = new ArrayList<MavenRemoteRepository>(infos.size());
    for (MavenRepositoryInfo each : infos) {
      if (each.getUrl() != null) {
        result.add(new MavenRemoteRepository(each.getId(), each.getName(), each.getUrl(), null, null, null));
      }
    }
    return result;
  }

  public static MavenId getMavenId(@NotNull String coord) {
    final String[] parts = coord.split(":");
    return new MavenId(parts.length > 0 ? parts[0] : null,
                       parts.length > 1 ? parts[1] : null,
                       parts.length > 2 ? parts[2] : null);
  }
}
