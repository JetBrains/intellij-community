// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.model.Model;
import org.apache.maven.repository.internal.MavenWorkspaceReader;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Maven40WorkspaceReader implements MavenWorkspaceReader {

  private final WorkspaceRepository myRepository = new WorkspaceRepository();
  private final WorkspaceReader myWorkspaceReader;
  private final Map<MavenId, Model> myMavenModelMap;

  public Maven40WorkspaceReader(@Nullable WorkspaceReader workspaceReader, @NotNull Map<MavenId, Model> mavenModelMap) {
    myWorkspaceReader = workspaceReader;
    myMavenModelMap = mavenModelMap;
  }

  @Override
  public WorkspaceRepository getRepository() {
    return myRepository;
  }

  @Override
  public File findArtifact(Artifact artifact) {
    return myWorkspaceReader == null ? null : myWorkspaceReader.findArtifact(artifact);
  }

  @Override
  public List<String> findVersions(Artifact artifact) {
    return myWorkspaceReader == null ? Collections.emptyList() : myWorkspaceReader.findVersions(artifact);
  }

  @Override
  public Model findModel(Artifact artifact) {
    return myMavenModelMap.get(new MavenId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
  }
}
