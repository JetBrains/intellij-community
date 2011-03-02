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

import gnu.trove.THashSet;
import org.apache.maven.artifact.Artifact;
import org.jetbrains.idea.maven.model.MavenId;

import java.util.Set;

public class UnresolvedArtifactsCollector {
  private final boolean myFailOnUnresolved;
  private final Set<MavenId> myUnresolvedIds = new THashSet<MavenId>();

  public UnresolvedArtifactsCollector(boolean failOnUnresolved) {
    myFailOnUnresolved = failOnUnresolved;
  }

  public void retrieveUnresolvedIds(Set<MavenId> result) {
    synchronized (myUnresolvedIds) {
      result.addAll(myUnresolvedIds);
      myUnresolvedIds.clear();
    }
  }

  public void collectAndSetResolved(Artifact artifact) {
    if (!artifact.isResolved()) {
      synchronized (myUnresolvedIds) {
        myUnresolvedIds.add(MavenModelConverter.createMavenId(artifact));
      }
    }
    if (!myFailOnUnresolved) artifact.setResolved(true);
  }
}
