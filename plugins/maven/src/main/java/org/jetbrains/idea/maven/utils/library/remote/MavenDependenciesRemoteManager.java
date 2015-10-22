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
package org.jetbrains.idea.maven.utils.library.remote;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.ProjectBundle;
import org.jetbrains.idea.maven.utils.library.RepositoryAttachHandler;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;
import org.jetbrains.idea.maven.utils.library.RepositoryUtils;

import java.util.List;

public class MavenDependenciesRemoteManager
  extends MavenRemoteManager<List<OrderRoot>, MavenDependenciesRemoteManager.Argument, MavenDependenciesRemoteManager>
  implements MavenRemoteTask<List<OrderRoot>, MavenDependenciesRemoteManager.Argument> {

  public MavenDependenciesRemoteManager(Project project) {
    super(project);
  }

  public static MavenDependenciesRemoteManager getInstance(Project project) {
    return project.getComponent(MavenDependenciesRemoteManager.class);
  }

  public void downloadDependenciesAsync(
    RepositoryLibraryProperties libraryProperties,
    boolean downloadSources,
    boolean downloadJavaDocs,
    String copyTo,
    MavenRemoteTask.ResultProcessor<List<OrderRoot>> resultProcessor) {
    schedule(this, new Argument(libraryProperties, downloadSources, downloadJavaDocs, copyTo), resultProcessor, false);
  }

  public
  @Nullable
  List<OrderRoot> downloadDependencies(
    RepositoryLibraryProperties libraryProperties,
    boolean downloadSources,
    boolean downloadJavaDocs,
    String copyTo) {
    return getSynchronously(this, new Argument(libraryProperties, downloadSources, downloadJavaDocs, copyTo));
  }

  @Nullable
  public List<OrderRoot> downloadDependenciesModal(
    final RepositoryLibraryProperties libraryProperties,
    final boolean downloadSources,
    final boolean downloadJavaDocs,
    final String copyTo) {
    return getSynchronouslyWithModal(this,
                                     new Argument(libraryProperties, downloadSources, downloadJavaDocs, copyTo),
                                     ProjectBundle.message("maven.resolving"));
  }

  @Override
  @NotNull
  public List<OrderRoot> execute(@NotNull Argument arg, ProgressIndicator indicator) {
    String coordinates = arg.libraryProperties.getGroupId() + ":" +
                         arg.libraryProperties.getArtifactId() + ":" +
                         RepositoryUtils.resolveEffectiveVersion(myProject, arg.libraryProperties);
    return RepositoryAttachHandler.resolveAndDownloadImpl(
      myProject,
      coordinates,
      arg.downloadJavaDocs,
      arg.downloadSources,
      arg.copyTo,
      RepositoryLibraryDescription.findDescription(arg.libraryProperties).getRemoteRepositories(),
      indicator);
  }

  @Override
  public String getName(@NotNull Argument arg) {
    RepositoryLibraryDescription libraryDescription = RepositoryLibraryDescription.findDescription(arg.libraryProperties);
    return ProjectBundle.message("maven.loading.library.hint", libraryDescription.getDisplayName());
  }

  public static class Argument {
    public RepositoryLibraryProperties libraryProperties;
    public boolean downloadSources;
    public boolean downloadJavaDocs;
    @Nullable public String copyTo;

    public Argument(RepositoryLibraryProperties libraryProperties,
                    boolean downloadSources,
                    boolean downloadJavaDocs,
                    @Nullable String copyTo) {
      this.libraryProperties = libraryProperties;
      this.downloadSources = downloadSources;
      this.downloadJavaDocs = downloadJavaDocs;
      this.copyTo = copyTo;
    }
  }
}
