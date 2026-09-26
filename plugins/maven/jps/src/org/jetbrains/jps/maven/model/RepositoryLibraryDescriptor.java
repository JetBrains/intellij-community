/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.maven.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public class RepositoryLibraryDescriptor {
  private final String myMavenId;
  private final String myGroupId;
  private final String myArtifactId;
  private final String myVersion;

  public RepositoryLibraryDescriptor(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
    myGroupId = groupId;
    myArtifactId = artifactId;
    myVersion = version;
    myMavenId = groupId + ":" + artifactId + ":" + version;
  }

  public RepositoryLibraryDescriptor(@Nullable String mavenId) {
    myMavenId = mavenId;
    if (mavenId == null) {
      myGroupId = myArtifactId = myVersion = null;
    }
    else {
      String[] parts = mavenId.split(":");
      myGroupId = parts.length > 0 ? parts[0] : null;
      myArtifactId = parts.length > 1 ? parts[1] : null;
      myVersion = parts.length > 2 ? parts[2] : null;
    }
  }


  public String getMavenId() {
    return myMavenId;
  }

  public String getGroupId() {
    return myGroupId;
  }

  public String getArtifactId() {
    return myArtifactId;
  }

  public String getVersion() {
    return myVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RepositoryLibraryDescriptor that = (RepositoryLibraryDescriptor)o;

    if (myMavenId != null ? !myMavenId.equals(that.myMavenId) : that.myMavenId != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myMavenId != null ? myMavenId.hashCode() : 0;
  }
}
