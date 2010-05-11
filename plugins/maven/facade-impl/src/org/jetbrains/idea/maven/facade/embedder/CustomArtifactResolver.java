/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.DefaultArtifactResolver;
import org.jetbrains.idea.maven.model.MavenId;

import java.io.File;
import java.util.List;
import java.util.Map;

public class CustomArtifactResolver extends DefaultArtifactResolver {
  private Map<MavenId, File> myProjectIdToFileMap;
  private UnresolvedArtifactsCollector myUnresolvedCollector;

  public void customize(Map<MavenId, File> projectIdToFileMap, boolean failOnUnresolved) {
    myProjectIdToFileMap = projectIdToFileMap;
    myUnresolvedCollector = new UnresolvedArtifactsCollector(failOnUnresolved);
  }

  public void reset() {
    myProjectIdToFileMap = null;
    myUnresolvedCollector = null;
  }

  public UnresolvedArtifactsCollector getUnresolvedCollector() {
    return myUnresolvedCollector;
  }

  @Override
  public void resolve(Artifact artifact, List remoteRepositories, ArtifactRepository localRepository)
    throws ArtifactResolutionException, ArtifactNotFoundException {
    if (resolveAsModule(artifact)) return;
    try {
      super.resolve(artifact, remoteRepositories, localRepository);
    }
    catch (AbstractArtifactResolutionException e) {
      myUnresolvedCollector.collectAndSetResolved(artifact);
    }
  }

  @Override
  public void resolveAlways(Artifact artifact, List remoteRepositories, ArtifactRepository localRepository)
    throws ArtifactResolutionException, ArtifactNotFoundException {
    if (resolveAsModule(artifact)) return;
    try {
      super.resolveAlways(artifact, remoteRepositories, localRepository);
    }
    catch (AbstractArtifactResolutionException e) {
      myUnresolvedCollector.collectAndSetResolved(artifact);
    }
  }

  private boolean resolveAsModule(Artifact a) {
    // method is called from different threads, so we have to copy the reference so ensure there is no race conditions.
    Map<MavenId, File> map = myProjectIdToFileMap;
    if (map == null) return false;

    File file = map.get(MavenModelConverter.createMavenId(a));
    if (file == null) return false;

    a.setResolved(true);
    a.setFile(file);

    return true;
  }
}
