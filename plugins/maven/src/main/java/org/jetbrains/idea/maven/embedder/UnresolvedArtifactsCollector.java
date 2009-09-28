package org.jetbrains.idea.maven.embedder;

import gnu.trove.THashSet;
import org.apache.maven.artifact.Artifact;
import org.jetbrains.idea.maven.project.MavenId;

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
        myUnresolvedIds.add(new MavenId(artifact));
      }
    }
    if (!myFailOnUnresolved) artifact.setResolved(true);
  }
}
