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
import com.intellij.CommonBundle;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.ProjectBundle;
import org.jetbrains.idea.maven.utils.library.remote.MavenDependenciesRemoteManager;
import org.jetbrains.idea.maven.utils.library.remote.MavenRemoteTask;
import org.jetbrains.idea.maven.utils.library.remote.MavenVersionsRemoteManager;

import java.util.*;

public class RepositoryUtils {
  @NotNull static public final String LatestVersionId = "LATEST";
  @NotNull static public final String LatestVersionDisplayName = "Latest";
  @NotNull static public final String ReleaseVersionId = "RELEASE";
  @NotNull static public final String ReleaseVersionDisplayName = "Release";
  @NotNull static public final String SnapshotVersionSuffix = "-SNAPSHOT";
  @NotNull static public final String DefaultVersionId = ReleaseVersionId;

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
    return getStorageRoot(library.getUrls(OrderRootType.CLASSES), project);
  }


  public static String getStorageRoot(String[] urls, Project project) {
    if (urls.length == 0) {
      return null;
    }
    final String localRepositoryPath =
      FileUtil.toSystemIndependentName(MavenProjectsManager.getInstance(project).getLocalRepository().getPath());
    List<String> roots = JBIterable.of(urls).transform(urlWithPrefix -> {
      String url = StringUtil.trimStart(urlWithPrefix, JarFileSystem.PROTOCOL_PREFIX);
      return url.startsWith(localRepositoryPath) ? null : FileUtil.toSystemDependentName(PathUtil.getParentPath(url));
    }).toList();
    Map<String, Integer> counts = new HashMap<>();
    for (String root : roots) {
      int count = counts.get(root) != null ? counts.get(root) : 0;
      counts.put(root, count + 1);
    }
    return Collections.max(counts.entrySet(), (o1, o2) -> o1.getValue().compareTo(o2.getValue())).getKey();
  }

  public static String resolveEffectiveVersion(@NotNull Project project, @NotNull RepositoryLibraryProperties properties) {
    String version = properties.getVersion();
    boolean isLatest = LatestVersionId.equals(version);
    boolean isRelease = ReleaseVersionId.equals(version);
    if (isLatest || isRelease) {
      Iterable<String> versions = MavenVersionsRemoteManager.getInstance(project).getMavenArtifactVersions(
        properties.getGroupId(),
        properties.getArtifactId());
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

  public static void loadDependencies(@NotNull final Project project,
                                      @NotNull final LibraryEx library,
                                      boolean downloadSources,
                                      boolean downloadJavaDocs,
                                      @Nullable String copyTo) {
    if (library.getKind() != RepositoryLibraryType.REPOSITORY_LIBRARY_KIND) {
      return;
    }
    final RepositoryLibraryProperties properties = (RepositoryLibraryProperties)library.getProperties();

    MavenDependenciesRemoteManager.getInstance(project)
      .downloadDependenciesAsync(
        properties,
        downloadSources,
        downloadJavaDocs,
        copyTo,
        new MavenRemoteTask.ResultProcessor<List<OrderRoot>>() {
          @Override
          public void process(final @Nullable List<OrderRoot> roots) {
            if (roots == null || roots.isEmpty()) {
              ApplicationManager.getApplication().invokeLater(
                () -> Messages.showErrorDialog(project, ProjectBundle.message("maven.downloading.failed", properties.getMavenId()),
                                             CommonBundle.getErrorTitle()));
              return;
            }
            ApplicationManager.getApplication().invokeLater(() -> {
              if (library.isDisposed()) {
                return;
              }
              AccessToken token = WriteAction.start();
              try {
                final NewLibraryEditor editor = new NewLibraryEditor(null, properties);
                editor.removeAllRoots();
                editor.addRoots(roots);
                final Library.ModifiableModel model = library.getModifiableModel();
                editor.applyTo((LibraryEx.ModifiableModelEx)model);
                model.commit();
              }
              finally {
                token.finish();
              }
            });
          }
        }
      );
  }

  public static void reloadDependencies(@NotNull final Project project,
                                        @NotNull final LibraryEx library) {
    loadDependencies(project, library, libraryHasSources(library), libraryHasJavaDocs(library), getStorageRoot(library, project));
  }
}
