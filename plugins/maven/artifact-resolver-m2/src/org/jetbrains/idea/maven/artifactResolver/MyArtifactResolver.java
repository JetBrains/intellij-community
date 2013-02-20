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
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.DefaultArtifactResolver;
import org.jetbrains.idea.maven.artifactResolver.common.MavenModuleMap;

import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class MyArtifactResolver extends DefaultArtifactResolver {

  @Override
  public void resolve(Artifact artifact, List remoteRepositories, ArtifactRepository localRepository)
    throws ArtifactResolutionException, ArtifactNotFoundException {
    if (!MavenModuleMap.getInstance().resolveToModule(artifact)) {
      super.resolve(artifact, remoteRepositories, localRepository);
    }
  }

  @Override
  public void resolveAlways(Artifact artifact, List remoteRepositories, ArtifactRepository localRepository)
    throws ArtifactResolutionException, ArtifactNotFoundException {
    if (!MavenModuleMap.getInstance().resolveToModule(artifact)) {
      super.resolveAlways(artifact, remoteRepositories, localRepository);
    }
  }

}
