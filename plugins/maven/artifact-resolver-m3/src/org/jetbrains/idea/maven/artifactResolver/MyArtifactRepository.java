/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.artifactResolver;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.repository.LocalArtifactRepository;
import org.jetbrains.idea.maven.artifactResolver.common.MavenModuleMap;

/**
 * @author Sergey Evdokimov
 */
public class MyArtifactRepository extends LocalArtifactRepository {

  protected boolean resolveAsModule(Artifact artifact) {
    if (artifact == null) {
      return false;
    }

    return MavenModuleMap.getInstance().resolveToModule(artifact);
  }

  public Artifact find(Artifact artifact) {
    resolveAsModule(artifact);
    return artifact;
  }

  public boolean hasLocalMetadata() {
    return false;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof MyArtifactRepository;
  }
}
