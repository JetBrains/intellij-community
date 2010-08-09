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
package org.jetbrains.idea.maven.facade.embedder;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.metadata.*;
import org.jetbrains.idea.maven.model.MavenId;

import java.io.File;
import java.util.List;
import java.util.Map;

public class CustomRepositoryMetadataManager extends DefaultRepositoryMetadataManager {
  private Map<MavenId, File> myProjectIdToFileMap;

  public void customize(Map<MavenId, File> projectIdToFileMap) {
    myProjectIdToFileMap = projectIdToFileMap;
  }

  public void reset() {
    myProjectIdToFileMap = null;
  }

  @Override
  public void resolve(RepositoryMetadata metadata, List remoteRepositories, ArtifactRepository localRepository)
    throws RepositoryMetadataResolutionException {
    super.resolve(metadata, remoteRepositories, localRepository);

    Map<MavenId, File> map = myProjectIdToFileMap;
    if (map == null) return;

    Metadata data = metadata.getMetadata();
    Versioning versioning = data.getVersioning();
    if (versioning == null) {
      data.setVersioning(versioning = new Versioning());
    }

    for (MavenId each : map.keySet()) {
      if (each.equals(data.getGroupId(), data.getArtifactId())) {
        versioning.addVersion(each.getVersion());
      }
    }
  }
}
