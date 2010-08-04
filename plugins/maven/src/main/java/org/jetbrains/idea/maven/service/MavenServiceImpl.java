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
import org.jetbrains.idea.maven.facade.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenEmbeddersManager;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class MavenServiceImpl extends MavenService {

  private final MavenEmbeddersManager myEmbeddersManager;

  public MavenServiceImpl(MavenProjectsManager projectsManager) {
    myEmbeddersManager = projectsManager.getEmbeddersManager();
  }

  @Override
  public Artifact[] getVersions(String groupId, String artifactId) {
    return new Artifact[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public Artifact[] resolveDependencies(Artifact[] artifact, String[] repositories) {
    MavenEmbedderWrapper wrapper = myEmbeddersManager.getEmbedder(MavenEmbeddersManager.FOR_DOWNLOAD);
    List<MavenArtifact> artifacts = null;
    return artifacts.toArray(new Artifact[artifacts.size()]);
  }

  @Override
  public DownloadResult[] downloadArtifacts(Artifact[] artifacts, int options) {
    return new DownloadResult[0];  //To change body of implemented methods use File | Settings | File Templates.
  }
}
