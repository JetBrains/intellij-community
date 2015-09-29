/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils.library;

import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class RepositoryLibraryProperties extends LibraryProperties<RepositoryLibraryProperties> {
  private String mavenId;
  private String groupId;
  private String artifactId;
  private String version;

  public RepositoryLibraryProperties() {
  }

  public RepositoryLibraryProperties(String mavenId) {
    setMavenId(mavenId);
  }

  public RepositoryLibraryProperties(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
    this.mavenId = groupId + ":" + artifactId + ":" + version;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof RepositoryLibraryProperties)) {
      return false;
    }
    RepositoryLibraryProperties other = (RepositoryLibraryProperties)obj;
    return Comparing.equal(mavenId, other.mavenId);

  }

  @Override
  public int hashCode() {
    return Comparing.hashcode(getMavenId());
  }

  @Override
  public RepositoryLibraryProperties getState() {
    return this;
  }

  @Override
  public void loadState(RepositoryLibraryProperties state) {
    setMavenId(state.mavenId);
  }

  @Attribute("maven-id")
  public String getMavenId() {
    return mavenId;
  }

  public void setMavenId(String mavenId) {
    this.mavenId = mavenId;
    if (mavenId == null) {
      groupId = artifactId = version = null;
    }
    else {
      String[] parts = mavenId.split(":");
      groupId = parts.length > 0 ? parts[0] : null;
      artifactId = parts.length > 1 ? parts[1] : null;
      version = parts.length > 2 ? parts[2] : null;
    }
  }

  public String getGroupId() {
    return groupId;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public String getVersion() {
    return version;
  }

  public void changeVersion(String version) {
    this.version = version;
    this.mavenId = groupId + ":" + artifactId + ":" + version;
  }
}
