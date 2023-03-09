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
package org.jetbrains.idea.maven.model;

import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;

public class MavenArchetype implements Serializable {
  public final @NotNull String groupId;
  public final @NotNull String artifactId;
  public final @NotNull String version;
  public final @Nullable String repository;
  public final @Nullable String description;

  public MavenArchetype(@NotNull String groupId,
                       @NotNull String artifactId,
                       @NotNull String version,
                       @Nullable String repository,
                       @Nullable String description) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
    this.repository = StringUtilRt.isEmptyOrSpaces(repository) ? null : repository;
    this.description = StringUtilRt.isEmptyOrSpaces(description) ? null : description;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenArchetype that = (MavenArchetype)o;

    return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId) && Objects.equals(version, that.version);
  }

  @Override
  public int hashCode() {
    int result = groupId.hashCode();
    result = 31 * result + artifactId.hashCode();
    result = 31 * result + version.hashCode();
    return result;
  }
}
