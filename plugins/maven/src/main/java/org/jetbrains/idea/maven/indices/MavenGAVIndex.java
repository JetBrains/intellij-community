// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices;


import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.RepositoryKind;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;

import java.util.Collection;
import java.util.Set;

public interface MavenGAVIndex {
  Collection<String> getGroupIds();

  Set<String> getArtifactIds(String groupId);

  Set<String> getVersions(String groupId, String artifactId);

  boolean hasGroupId(String groupId);

  boolean hasArtifactId(String groupId, String artifactId);

  boolean hasVersion(String groupId, String artifactId, String version);

  RepositoryKind getKind();

  @Nullable MavenRepositoryInfo getRepository();
}
