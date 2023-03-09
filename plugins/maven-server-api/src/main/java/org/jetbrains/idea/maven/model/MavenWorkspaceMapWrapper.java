// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.model;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MavenWorkspaceMapWrapper {
  private final MavenWorkspaceMap myWorkspaceMap;

  private final Map<String, Set<MavenId>> myArtifactToIdToMavenIdMapping = new HashMap<>();

  public MavenWorkspaceMapWrapper(MavenWorkspaceMap workspaceMap) {
    myWorkspaceMap = workspaceMap;

    if (null != myWorkspaceMap) {
      for (MavenId mavenId : myWorkspaceMap.getAvailableIds()) {
        if (!myArtifactToIdToMavenIdMapping.containsKey(mavenId.getArtifactId())) {
          myArtifactToIdToMavenIdMapping.put(mavenId.getArtifactId(), new HashSet<>());
        }
        myArtifactToIdToMavenIdMapping.get(mavenId.getArtifactId()).add(mavenId);
      }
    }
  }

  public MavenWorkspaceMap.Data findFileAndOriginalId(MavenId mavenId) {
    return myWorkspaceMap.findFileAndOriginalId(mavenId);
  }

  @NotNull
  public Set<MavenId> getAvailableIdsForArtifactId(String artifactId) {
    Set<MavenId> ids = myArtifactToIdToMavenIdMapping.get(artifactId);
    return null == ids ? Collections.emptySet() : ids;
  }
}
