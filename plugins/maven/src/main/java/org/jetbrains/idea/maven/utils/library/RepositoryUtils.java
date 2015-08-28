/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.MavenExtraArtifactType;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.ProjectBundle;

import java.util.*;

public class RepositoryUtils {
  @NotNull static public final String LatestVersionId = "LATEST";
  @NotNull static public final String ReleaseVersionId = "RELEASE";
  @NotNull static public final String SnapshotVersionSuffix = "-SNAPSHOT";

  public static void downloadAsync(final Project project,
                                   final boolean downloadSources,
                                   final boolean downloadJavaDocs,
                                   @NotNull final RepositoryLibraryProperties properties) {
    String displayName = RepositoryLibraryDescription.findDescription(properties).getDisplayName();
    Task task = new Task.Backgroundable(project, ProjectBundle.message("maven.loading.library.hint", displayName), false) {
      public void run(@NotNull ProgressIndicator indicator) {
        List<OrderRoot> download = download(project, downloadSources, downloadJavaDocs, properties);
      }
    };
    ProgressManager.getInstance().run(task);
  }
  public static List<OrderRoot> download(Project project,
                                         boolean downloadSources,
                                         boolean downloadJavaDocs,
                                         @NotNull RepositoryLibraryProperties properties) {
    String coordinates = properties.getGroupId() + ":" +
                         properties.getArtifactId() + ":" +
                         resolveEffectiveVersion(project, properties);
    return RepositoryAttachHandler.resolveAndDownloadImpl(
      project,
      coordinates,
      downloadJavaDocs,
      downloadSources,
      null,
      RepositoryLibraryDescription.findDescription(properties).getRemoteRepositories());
  }

  public static boolean libraryHasSources(@Nullable Library library) {
    return library != null && library.getUrls(OrderRootType.SOURCES).length > 0;
  }

  public static boolean libraryHasSources(@Nullable LibraryEditor libraryEditor) {
    return libraryEditor != null && libraryEditor.getUrls(OrderRootType.SOURCES).length > 0;
  }

  public static boolean libraryHasJavaDocs(@Nullable Library library) {
    return library != null && library.getUrls(JavadocOrderRootType.getInstance()).length > 0;
  }

  public static boolean libraryHasJavaDocs(@Nullable LibraryEditor libraryEditor) {
    return libraryEditor != null && libraryEditor.getUrls(JavadocOrderRootType.getInstance()).length > 0;
  }

  public static String getStorageRoot(Library library, Project project) {
    final String[] urls = library.getUrls(OrderRootType.CLASSES);
    if (urls.length == 0) {
      return null;
    }
    final String localRepositoryPath =
      FileUtil.toSystemIndependentName(MavenProjectsManager.getInstance(project).getLocalRepository().getPath());
    List<String> roots = JBIterable.of(urls).transform(new Function<String, String>() {
      @Override
      public String fun(String urlWithPrefix) {
        String url = StringUtil.trimStart(urlWithPrefix, JarFileSystem.PROTOCOL_PREFIX);
        return url.startsWith(localRepositoryPath) ? null : FileUtil.toSystemDependentName(PathUtil.getParentPath(url));
      }
    }).toList();
    Map<String, Integer> counts = new HashMap<String, Integer>();
    for (String root : roots) {
      int count = counts.get(root) != null ? counts.get(root) : 0;
      counts.put(root, count + 1);
    }
    return Collections.max(counts.entrySet(), new Comparator<Map.Entry<String, Integer>>() {
      @Override
      public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
        return o1.getValue().compareTo(o2.getValue());
      }
    }).getKey();
  }

  private static String resolveEffectiveVersion(@NotNull Project project, @NotNull RepositoryLibraryProperties properties) {
    String version = properties.getVersion();
    boolean isLatest = LatestVersionId.equals(version);
    boolean isRelease = ReleaseVersionId.equals(version);
    if (isLatest || isRelease) {
      Iterable<String> versions = RepositoryAttachHandler.retrieveVersions(
        project,
        properties.getGroupId(),
        properties.getArtifactId(),
        RepositoryLibraryDescription.findDescription(properties).getRemoteRepositories());
      if (isRelease) {
        versions = Iterables.filter(versions, new Predicate<String>() {
          @Override
          public boolean apply(String input) {
            return !input.endsWith(SnapshotVersionSuffix);
          }
        });
      }
      Iterator<String> iterator = versions.iterator();
      if (iterator.hasNext()) {
        version = iterator.next();
      }
    }
    return version;
  }

  public static void reloadDependenciesAsync(@NotNull final Project project,
                                             @NotNull final LibraryEx library) {
    Task task = new Task.Backgroundable(project, "Maven", false) {
      public void run(@NotNull ProgressIndicator indicator) {
        reloadDependencies(
          indicator,
          project,
          library);
      }
    };
    ProgressManager.getInstance().run(task);
  }

  public static List<MavenExtraArtifactType> createExtraArtifactTypeList(boolean sources, boolean javaDocs) {
    List<MavenExtraArtifactType> result = new ArrayList<MavenExtraArtifactType>();
    if (sources) {
      result.add(MavenExtraArtifactType.SOURCES);
    }
    if (javaDocs) {
      result.add(MavenExtraArtifactType.DOCS);
    }
    return result;
  }

  public static synchronized void loadDependencies(@NotNull ProgressIndicator indicator,
                                                   @NotNull final Project project,
                                                   RepositoryLibraryProperties libraryProperties,
                                                   boolean downloadSources,
                                                   boolean downloadJavaDocs,
                                                   String displayName,
                                                   Processor<List<MavenArtifact>> processor) {
    indicator.setText(ProjectBundle.message("maven.loading.library.hint", displayName));

    RepositoryAttachHandler.doResolveInner(
      project,
      Collections
        .singletonList(new MavenId(libraryProperties.getGroupId(), libraryProperties.getArtifactId(),
                                   resolveEffectiveVersion(project, libraryProperties))),
      createExtraArtifactTypeList(downloadSources, downloadJavaDocs),
      RepositoryLibraryDescription.findDescription(libraryProperties).getRemoteRepositories(),
      processor,
      indicator);
  }


  public static void loadDependencies(@NotNull ProgressIndicator indicator,
                                      @NotNull final Project project,
                                      @NotNull final LibraryEx library,
                                      boolean downloadSources,
                                      boolean downloadJavaDocs) {
    final String storageRoot = getStorageRoot(library, project);
    final RepositoryLibraryProperties libraryProperties = (RepositoryLibraryProperties)library.getProperties();
    loadDependencies(indicator,
                     project,
                     libraryProperties,
                     downloadSources,
                     downloadJavaDocs,
                     library.getName(),
                     new Processor<List<MavenArtifact>>() {
                       @Override
                       public boolean process(List<MavenArtifact> artifacts) {
                         if (artifacts == null || artifacts.isEmpty()) {
                           return true;
                         }
                         final List<OrderRoot> roots = RepositoryAttachHandler.createRoots(artifacts, storageRoot);
                         ApplicationManager.getApplication().invokeLater(new Runnable() {
                           @Override
                           public void run() {
                             if (library.isDisposed()) {
                               return;
                             }
                             AccessToken token = WriteAction.start();
                             try {
                               final NewLibraryEditor editor = new NewLibraryEditor(null, libraryProperties);
                               editor.removeAllRoots();
                               editor.addRoots(roots);
                               final Library.ModifiableModel model = library.getModifiableModel();
                               editor.applyTo((LibraryEx.ModifiableModelEx)model);
                               model.commit();
                             }
                             finally {
                               token.finish();
                             }
                           }
                         });
                         return true;
                       }
                     });
  }

  public static void reloadDependencies(@NotNull ProgressIndicator indicator,
                                        @NotNull final Project project,
                                        @NotNull final LibraryEx library) {
    loadDependencies(indicator, project, library, libraryHasSources(library), libraryHasJavaDocs(library));
  }
}
