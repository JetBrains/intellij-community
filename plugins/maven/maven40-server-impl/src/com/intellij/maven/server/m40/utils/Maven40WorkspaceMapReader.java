// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.api.model.Model;
import org.apache.maven.impl.resolver.MavenWorkspaceReader;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenWorkspaceMap;
import org.jetbrains.idea.maven.model.MavenWorkspaceMapWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Maven40WorkspaceMapReader implements WorkspaceReader, MavenWorkspaceReader {

  private final WorkspaceRepository myRepository = new WorkspaceRepository();

  private final MavenWorkspaceMapWrapper myWorkspaceMap;
  private final ConcurrentHashMap<MavenId, Model> myMavenModelMap;

  public Maven40WorkspaceMapReader(MavenWorkspaceMap workspaceMap) {
    myWorkspaceMap = new MavenWorkspaceMapWrapper(workspaceMap, new Properties());
    myMavenModelMap = new ConcurrentHashMap<>();
  }

  @Override
  public WorkspaceRepository getRepository() {
    return myRepository;
  }

  @Override
  public File findArtifact(Artifact artifact) {
    MavenWorkspaceMap.Data resolved =
      myWorkspaceMap.findFileAndOriginalId(new MavenId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
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

  @Override
  public Model findModel(Artifact artifact) {
    return myMavenModelMap.get(new MavenId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
  }


  void setCacheModelMap(Map<MavenId, Model> map) {
    myMavenModelMap.clear();
    myMavenModelMap.putAll(map);
  }
}
