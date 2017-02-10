/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.roots.DependencyScope;
import com.intellij.util.containers.HashMap;
import icons.MavenIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class RepositoryLibraryDescription {
  protected static final MavenRepositoryInfo mavenCentralRepository = new MavenRepositoryInfo(
    "central",
    "Maven Central repository",
    "http://repo1.maven.org/maven2");
  protected static final MavenRepositoryInfo jbossCommunityRepository = new MavenRepositoryInfo(
    "jboss.community",
    "JBoss Community repository",
    "http://repository.jboss.org/nexus");
  private static final List<MavenRepositoryInfo> defaultRemoteRepositories =
    Arrays.asList(mavenCentralRepository, jbossCommunityRepository);
  private static Map<String, RepositoryLibraryDescription> registeredLibraries;
  private final String groupId;
  private final String artifactId;
  private final String libraryName;

  protected RepositoryLibraryDescription(String groupId, String artifactId, String libraryName) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.libraryName = libraryName;
  }

  @NotNull
  public static synchronized RepositoryLibraryDescription findDescription(@NotNull final String groupId, @NotNull final String artifactId) {
    if (registeredLibraries == null) {
      registeredLibraries = new HashMap<>();
      for (RepositoryLibraryBean bean : RepositoryLibraryBean.EP_NAME.getExtensions()) {
        String id = bean.groupId + ":" + bean.artifactId;
        registeredLibraries.put(id, new RepositoryLibraryDescription(
          bean.groupId,
          bean.artifactId,
          bean.name));
      }
    }
    final String id = groupId + ":" + artifactId;
    RepositoryLibraryDescription description = registeredLibraries.get(id);
    if (description != null) {
      return description;
    }
    return new RepositoryLibraryDescription(groupId, artifactId, id);
  }

  @NotNull
  public static synchronized RepositoryLibraryDescription findDescription(@NotNull final RepositoryLibraryProperties properties) {
    return findDescription(properties.getGroupId(), properties.getArtifactId());
  }

  @NotNull
  public String getGroupId() {
    return groupId;
  }

  @NotNull
  public String getArtifactId() {
    return artifactId;
  }

  @NotNull
  public String getDisplayName() {
    return libraryName;
  }

  @NotNull
  public Icon getIcon() {
    return MavenIcons.MavenLogo;
  }

  @Nullable
  public DependencyScope getSuggestedScope() {
    return null;
  }

  @NotNull
  public List<MavenRepositoryInfo> getRemoteRepositories() {
    return defaultRemoteRepositories;
  }

  // One library could have more then one description - for ex. in different plugins
  // In this case heaviest description will be used
  public int getWeight() {
    return 1000;
  }

  public RepositoryLibraryProperties createDefaultProperties() {
    return new RepositoryLibraryProperties(getGroupId(), getArtifactId(), RepositoryUtils.ReleaseVersionId);
  }

  public String getDisplayName(String version) {
    if (version.equals(RepositoryUtils.LatestVersionId)) {
      version = RepositoryUtils.LatestVersionDisplayName;
    }
    if (version.equals(RepositoryUtils.ReleaseVersionId)) {
      version = RepositoryUtils.ReleaseVersionDisplayName;
    }
    return getDisplayName() + ":" + version;
  }

  public String getMavenCoordinates(String version) {
    return getGroupId() + ":" + getArtifactId() + ":" + version;
  }



}
