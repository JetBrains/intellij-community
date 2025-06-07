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
import org.eclipse.aether.repository.WorkspaceRepository;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenWorkspaceMap;
import org.jetbrains.idea.maven.model.MavenWorkspaceMapWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

public class Maven3WorkspaceMapReader implements MavenWorkspaceReader {

  private final WorkspaceRepository myRepository = new WorkspaceRepository();

  private final MavenWorkspaceMapWrapper myWorkspaceMap;

  private final Map<MavenId, Model> myMavenModelMap = new HashMap<>();

  public Maven3WorkspaceMapReader(MavenWorkspaceMap workspaceMap, Properties systemProperties) {
    myWorkspaceMap = new MavenWorkspaceMapWrapper(workspaceMap, systemProperties);
  }

  @Override
  public WorkspaceRepository getRepository() {
    return myRepository;
  }

  @Override
  public File findArtifact(Artifact artifact) {
    MavenWorkspaceMap.Data resolved = myWorkspaceMap.findFileAndOriginalId(new MavenId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
    if (resolved == null) return null;

    return resolved.getFile(artifact.getExtension());
  }

  @Override
  public List<String> findVersions(Artifact artifact) {
    List<String> res = new ArrayList<>();

    Set<MavenId> ids = myWorkspaceMap.getAvailableIdsForArtifactId(artifact.getArtifactId());
    for (MavenId id : ids) {
      if (Objects.equals(id.getGroupId(), artifact.getGroupId())) {
        String version = id.getVersion();

        if (version != null) {
          res.add(version);
        }
      }
    }

    return res;
  }

  @Override
  public Model findModel(Artifact artifact) {
    return myMavenModelMap.get(new MavenId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
  }

  public void fillSessionCache(Map<MavenId, Model> map) {
    myMavenModelMap.putAll(map);
    for (MavenId mavenId : map.keySet()) {
      Model model = map.get(mavenId);
      if (null == model) continue;
      File pomFile = model.getPomFile();
      if (null == pomFile) continue;
      myWorkspaceMap.register(mavenId, pomFile);
    }
  }
}
