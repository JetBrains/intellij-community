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
package org.jetbrains.idea.maven.server;

import org.apache.maven.model.Model;
import org.apache.maven.repository.internal.MavenWorkspaceReader;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Maven3WorkspaceReader implements MavenWorkspaceReader {

  private final WorkspaceRepository myRepository = new WorkspaceRepository();
  private final WorkspaceReader myWorkspaceReader;
  private final Map<MavenId, Model> myMavenModelMap;

  public Maven3WorkspaceReader(@Nullable WorkspaceReader workspaceReader, @NotNull Map<MavenId, Model> mavenModelMap) {
    myWorkspaceReader = workspaceReader;
    myMavenModelMap = mavenModelMap;
  }

  @Override
  public WorkspaceRepository getRepository() {
    return myRepository;
  }

  @Override
  public File findArtifact(Artifact artifact) {
    return myWorkspaceReader == null ? null : myWorkspaceReader.findArtifact(artifact);
  }

  @Override
  public List<String> findVersions(Artifact artifact) {
    return myWorkspaceReader == null ? Collections.emptyList() : myWorkspaceReader.findVersions(artifact);
  }

  @Override
  public Model findModel(Artifact artifact) {
    return myMavenModelMap.get(new MavenId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
  }
}
