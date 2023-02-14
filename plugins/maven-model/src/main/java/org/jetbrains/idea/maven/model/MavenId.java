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
package org.jetbrains.idea.maven.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;

public class MavenId implements Serializable, MavenCoordinate {
  public static final String UNKNOWN_VALUE = "Unknown";

  @Nullable private final String myGroupId;
  @Nullable private final String myArtifactId;
  @Nullable private final String myVersion;

  public MavenId(@Nullable String groupId, @Nullable String artifactId, @Nullable String version) {
    myGroupId = groupId;
    myArtifactId = artifactId;
    myVersion = version;
  }

  public MavenId(@Nullable String coord) {
    if (coord == null) {
      myGroupId = myArtifactId = myVersion = null;
    }
    else {
      String[] parts = coord.split(":");
      myGroupId = parts.length > 0 ? parts[0] : null;
      myArtifactId = parts.length > 1 ? parts[1] : null;
      myVersion = parts.length > 2 ? parts[2] : null;
    }
  }

  @Override
  @Nullable
  public String getGroupId() {
    return myGroupId;
  }

  @Override
  @Nullable
  public String getArtifactId() {
    return myArtifactId;
  }

  @Override
  @Nullable
  public String getVersion() {
    return myVersion;
  }

  @NotNull
  public String getKey() {
    StringBuilder builder = new StringBuilder();

    append(builder, myGroupId);
    append(builder, myArtifactId);
    append(builder, myVersion);

    return builder.toString();
  }

  @NotNull
  public String getDisplayString() {
    return getKey();
  }

  public static void append(StringBuilder builder, String part) {
    if (builder.length() != 0) builder.append(':');
    appendFirst(builder, part);
  }

  public static void appendFirst(StringBuilder builder, String part) {
    builder.append(part == null ? "<unknown>" : part);
  }

  @Override
  public String toString() {
    return getDisplayString();
  }

  public boolean equals(@Nullable String groupId, @Nullable String artifactId) {
    if (!Objects.equals(myArtifactId, artifactId)) return false;
    if (!Objects.equals(myGroupId, groupId)) return false;
    return true;
  }

  public boolean equals(@Nullable String groupId, @Nullable String artifactId, @Nullable String version) {
    if (!equals(groupId, artifactId)) return false;
    if (!Objects.equals(myVersion, version)) return false;
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenId other = (MavenId)o;
    return equals(other.getGroupId(), other.myArtifactId, other.myVersion);
  }

  @Override
  public int hashCode() {
    int result;
    result = (myGroupId != null ? myGroupId.hashCode() : 0);
    result = 31 * result + (myArtifactId != null ? myArtifactId.hashCode() : 0);
    result = 31 * result + (myVersion != null ? myVersion.hashCode() : 0);
    return result;
  }
}
