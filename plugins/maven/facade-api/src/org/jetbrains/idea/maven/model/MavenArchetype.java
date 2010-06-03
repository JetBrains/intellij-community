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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

public class MavenArchetype implements Serializable {
  public final String groupId;
  public final String artifactId;
  public final String version;
  public final String repository;
  public final String description;

  public MavenArchetype(@NotNull String groupId,
                       @NotNull String artifactId,
                       @NotNull String version,
                       @Nullable String repository,
                       @Nullable String description) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
    this.repository = StringUtil.isEmptyOrSpaces(repository) ? null : repository;
    this.description = StringUtil.isEmptyOrSpaces(description) ? null : description;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenArchetype that = (MavenArchetype)o;

    if (groupId != null ? !groupId.equals(that.groupId) : that.groupId != null) return false;
    if (artifactId != null ? !artifactId.equals(that.artifactId) : that.artifactId != null) return false;
    if (version != null ? !version.equals(that.version) : that.version != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = groupId != null ? groupId.hashCode() : 0;
    result = 31 * result + (artifactId != null ? artifactId.hashCode() : 0);
    result = 31 * result + (version != null ? version.hashCode() : 0);
    return result;
  }
}
