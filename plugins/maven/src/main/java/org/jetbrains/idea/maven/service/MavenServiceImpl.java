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
package org.jetbrains.idea.maven.service;

import com.intellij.ide.mavenService.Artifact;
import com.intellij.ide.mavenService.DownloadResult;
import com.intellij.ide.mavenService.MavenService;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.maven.execution.SoutMavenConsole;
import org.jetbrains.idea.maven.facade.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.project.MavenEmbeddersManager;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class MavenServiceImpl extends MavenService {

  private static final Function<Artifact,MavenArtifactInfo> ARTIFACT_INFO_FUNCTION = new Function<Artifact, MavenArtifactInfo>() {
    @Override
    public MavenArtifactInfo fun(Artifact artifact) {
      return new MavenArtifactInfo(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), "pom", null);
    }
  };
  private static final Function<String,MavenRemoteRepository> REMOTE_REPOSITORY_FUNCTION = new Function<String, MavenRemoteRepository>() {
    @Override
    public MavenRemoteRepository fun(String s) {
      return new MavenRemoteRepository("0", null, s, null, null, null);
    }
  };
  private final MavenEmbeddersManager myEmbeddersManager;

  public MavenServiceImpl(MavenProjectsManager projectsManager) {
    myEmbeddersManager = projectsManager.getEmbeddersManager();
  }

  @Override
  public Artifact createArtifact(final String groupId, final String artifactId, final String versionId) {
    return new Artifact() {
      @Override
      public String getGroupId() {
        return groupId;
      }

      @Override
      public String getArtifactId() {
        return artifactId;
      }

      @Override
      public String getVersion() {
        return versionId;
      }
    };
  }

  @Override
  public Artifact[] getVersions(String groupId, String artifactId) {
    return new Artifact[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public List<Artifact> resolveDependencies(List<Artifact> artifact, List<String> repositories) {

    MavenEmbedderWrapper embedder = myEmbeddersManager.getEmbedder(MavenEmbeddersManager.FOR_DOWNLOAD);
    embedder.customizeForResolve(new SoutMavenConsole(), new MavenProgressIndicator(new EmptyProgressIndicator()));
    List<MavenArtifactInfo> infos = ContainerUtil.map(artifact, ARTIFACT_INFO_FUNCTION);
    List<MavenRemoteRepository> remoteRepositories = ContainerUtil.map(repositories, REMOTE_REPOSITORY_FUNCTION);
    try {
      List<MavenArtifact> artifacts = embedder.resolveTransitively(infos, remoteRepositories);
      return ContainerUtil.map(artifacts, new Function<MavenArtifact, Artifact>() {
        @Override
        public Artifact fun(MavenArtifact mavenArtifact) {
          return new ArtifactAdapter(mavenArtifact);
        }
      });
    }
    catch (MavenProcessCanceledException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public List<DownloadResult> downloadArtifacts(List<Artifact> artifacts, int options) {
    return null;
  }

  private static class ArtifactAdapter implements Artifact {
    private final MavenArtifact myMavenArtifact;

    public ArtifactAdapter(MavenArtifact mavenArtifact) {
      //To change body of created methods use File | Settings | File Templates.
      myMavenArtifact = mavenArtifact;
    }

    @Override
    public String getGroupId() {
      return myMavenArtifact.getGroupId();
    }

    @Override
    public String getArtifactId() {
      return myMavenArtifact.getArtifactId();
    }

    @Override
    public String getVersion() {
      return myMavenArtifact.getVersion();
    }
  }
}
