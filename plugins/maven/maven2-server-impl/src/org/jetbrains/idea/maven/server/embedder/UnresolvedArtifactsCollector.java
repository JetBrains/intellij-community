// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server.embedder;

import org.apache.maven.artifact.Artifact;
import org.jetbrains.idea.maven.model.MavenId;

import java.util.HashSet;
import java.util.Set;

public final class UnresolvedArtifactsCollector {
  private final boolean myFailOnUnresolved;
  private final Set<MavenId> myUnresolvedIds = new HashSet<MavenId>();

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
        myUnresolvedIds.add(Maven2ModelConverter.createMavenId(artifact));
      }
    }
    if (!myFailOnUnresolved) artifact.setResolved(true);
  }
}
