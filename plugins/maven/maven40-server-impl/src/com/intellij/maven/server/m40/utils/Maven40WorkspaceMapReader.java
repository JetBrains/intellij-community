// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenWorkspaceMap;
import org.jetbrains.idea.maven.model.MavenWorkspaceMapWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Maven40WorkspaceMapReader implements WorkspaceReader {

  private final WorkspaceRepository myRepository = new WorkspaceRepository();

  private final MavenWorkspaceMapWrapper myWorkspaceMap;

  public Maven40WorkspaceMapReader(MavenWorkspaceMap workspaceMap) {
    myWorkspaceMap = new MavenWorkspaceMapWrapper(workspaceMap);
  }

  @Override
  public WorkspaceRepository getRepository() {
    return myRepository;
  }

  @Override
  public File findArtifact(Artifact artifact) {
    MavenWorkspaceMap.Data resolved = myWorkspaceMap.findFileAndOriginalId(new MavenId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
    if (resolved == null) return null;

    return resolved.getFile(artifact.getExtension());
  }

  @Override
  public List<String> findVersions(Artifact artifact) {
    List<String> res = new ArrayList<>();

    Set<MavenId> ids = myWorkspaceMap.getAvailableIdsForArtifactId(artifact.getArtifactId());
    for (MavenId id : ids) {
      if (Objects.equals(id.getGroupId(), artifact.getGroupId())) {
        String version = id.getVersion();

        if (version != null) {
          res.add(version);
        }
      }
    }

    return res;
  }
}
