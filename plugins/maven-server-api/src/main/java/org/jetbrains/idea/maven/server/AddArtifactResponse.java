// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import java.io.File;
import java.io.Serializable;
import java.util.Objects;

public final class AddArtifactResponse implements Serializable {
  private final File artifactFile;
  private final IndexedMavenId indexedMavenId;

  public AddArtifactResponse(File artifactFile, IndexedMavenId indexedMavenId) {
    this.artifactFile = artifactFile;
    this.indexedMavenId = indexedMavenId;
  }

  public File artifactFile() { return artifactFile; }

  public IndexedMavenId indexedMavenId() { return indexedMavenId; }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    AddArtifactResponse that = (AddArtifactResponse)obj;
    return Objects.equals(this.artifactFile, that.artifactFile) &&
           Objects.equals(this.indexedMavenId, that.indexedMavenId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(artifactFile, indexedMavenId);
  }

  @Override
  public String toString() {
    return "AddArtifactResponse[" +
           "artifactFile=" + artifactFile + ", " +
           "indexedMavenId=" + indexedMavenId + ']';
  }
}
