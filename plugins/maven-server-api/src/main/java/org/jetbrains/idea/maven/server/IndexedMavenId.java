// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import java.io.Serializable;

public class IndexedMavenId implements Serializable {
  public final String groupId;
  public final String artifactId;
  public final String version;
  public final String packaging;
  public final String description;

  public IndexedMavenId(String groupId, String artifactId, String version, String packaging, String description) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
    this.packaging = packaging;
    this.description = description;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IndexedMavenId id = (IndexedMavenId)o;

    if (groupId != null ? !groupId.equals(id.groupId) : id.groupId != null) return false;
    if (artifactId != null ? !artifactId.equals(id.artifactId) : id.artifactId != null) return false;
    if (version != null ? !version.equals(id.version) : id.version != null) return false;
    if (packaging != null ? !packaging.equals(id.packaging) : id.packaging != null) return false;
    if (description != null ? !description.equals(id.description) : id.description != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = groupId != null ? groupId.hashCode() : 0;
    result = 31 * result + (artifactId != null ? artifactId.hashCode() : 0);
    result = 31 * result + (version != null ? version.hashCode() : 0);
    result = 31 * result + (packaging != null ? packaging.hashCode() : 0);
    result = 31 * result + (description != null ? description.hashCode() : 0);
    return result;
  }
}
