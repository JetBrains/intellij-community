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
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.importing.MavenExtraArtifactType;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.ProjectBundle;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public abstract class RepositoryLibraryTypeBase extends LibraryType<RepositoryLibraryProperties> {
  @NotNull static public final String LatestVersionId = "LATEST";
  @NotNull static public final String ReleaseVersionId = "RELEASE";
  @NotNull static public final String SnapshotVersionSuffix = "-SNAPSHOT";

  public static final PersistentLibraryKind<RepositoryLibraryProperties>
    REPOSITORY_LIBRARY_KIND = new PersistentLibraryKind<RepositoryLibraryProperties>("repository") {
    @NotNull
    @Override
    public RepositoryLibraryProperties createDefaultProperties() {
      return new RepositoryLibraryProperties();
    }
  };

  public RepositoryLibraryTypeBase() {
    super(REPOSITORY_LIBRARY_KIND);
  }

  public static List<OrderRoot> download(Project project, @NotNull RepositoryLibraryProperties properties) {
    String coordinates = properties.getGroupId() + ":" +
                         properties.getArtifactId() + ":" +
                         resolveEffectiveVersion(project, properties);
    return RepositoryAttachHandler.resolveAndDownloadImpl(
      project,
      coordinates,
      false,
      false,
      null,
      RepositoryLibraryDescription.findDescription(properties).getRemoteRepositories());
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

  public static void reloadDependencies(@NotNull ProgressIndicator indicator,
                                        @NotNull final Project project,
                                        @NotNull final LibraryEx library) {
    indicator.setText(ProjectBundle.message("maven.loading.library.hint", library.getName()));
    @NotNull final RepositoryLibraryProperties libraryProperties = (RepositoryLibraryProperties)library.getProperties();
    RepositoryAttachHandler.doResolveInner(
      project,
      Collections
        .singletonList(new MavenId(libraryProperties.getGroupId(), libraryProperties.getArtifactId(),
                                   resolveEffectiveVersion(project, libraryProperties))),
      new SmartList<MavenExtraArtifactType>(),
      RepositoryLibraryDescription.findDescription(libraryProperties).getRemoteRepositories(),
      new Processor<List<MavenArtifact>>() {
        @Override
        public boolean process(List<MavenArtifact> artifacts) {
          if (artifacts == null || artifacts.isEmpty()) {
            return true;
          }
          final List<OrderRoot> roots = RepositoryAttachHandler.createRoots(artifacts, null);
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
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
      },
      indicator);
  }
}
