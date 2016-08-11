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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenVersionComparable;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.project.MavenEmbeddersManager;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.ProjectBundle;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;

import java.util.*;

public class MavenVersionsRemoteManager
  extends MavenRemoteManager<List<String>, RepositoryLibraryDescription, MavenVersionsRemoteManager>
  implements MavenRemoteTask<List<String>, RepositoryLibraryDescription> {


  public MavenVersionsRemoteManager(Project project) {
    super(project);
  }

  public static MavenVersionsRemoteManager getInstance(Project project) {
    return project.getComponent(MavenVersionsRemoteManager.class);
  }

  public void getMavenArtifactVersionsAsync(String groupId,
                                            String artifactId,
                                            MavenRemoteTask.ResultProcessor<List<String>> resultProcessor) {
    schedule(this, RepositoryLibraryDescription.findDescription(groupId, artifactId), resultProcessor, false);
  }

  @Nullable
  public List<String> getMavenArtifactVersions(String groupId, String artifactId) {
    return getSynchronously(this, RepositoryLibraryDescription.findDescription(groupId, artifactId));
  }

  @NotNull
  @Override
  public List<String> execute(@NotNull RepositoryLibraryDescription repositoryLibraryDescription, ProgressIndicator indicator) {
    MavenEmbeddersManager manager = MavenProjectsManager.getInstance(myProject).getEmbeddersManager();
    MavenEmbedderWrapper embedder = manager.getEmbedder(MavenEmbeddersManager.FOR_GET_VERSIONS);
    embedder.customizeForGetVersions();
    try {
      List<MavenRemoteRepository> remoteRepositories = convertRepositories(repositoryLibraryDescription.getRemoteRepositories());
      List<String> versions = embedder.retrieveVersions(
        repositoryLibraryDescription.getGroupId(),
        repositoryLibraryDescription.getArtifactId(),
        remoteRepositories);
      Collections.sort(versions, (o1, o2) -> {
        MavenVersionComparable v1 = new MavenVersionComparable(o1);
        MavenVersionComparable v2 = new MavenVersionComparable(o2);
        return v2.compareTo(v1);
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

  private static List<MavenRemoteRepository> convertRepositories(Collection<MavenRepositoryInfo> infos) {
    List<MavenRemoteRepository> result = new ArrayList<>(infos.size());
    for (MavenRepositoryInfo each : infos) {
      if (each.getUrl() != null) {
        result.add(new MavenRemoteRepository(each.getId(), each.getName(), each.getUrl(), null, null, null));
      }
    }
    return result;
  }


  @Override
  public String getName(@NotNull RepositoryLibraryDescription repositoryLibraryDescription) {
    return ProjectBundle.message("maven.loading.library.version.hint", repositoryLibraryDescription.getDisplayName());
  }
}
