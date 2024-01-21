// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenWorkspaceMap;

import java.io.File;
import java.io.Serializable;
import java.util.*;

public class ProjectResolutionRequest implements Serializable {
  private final @NotNull Map<@NotNull File, String> fileToChecksum;
  private final @NotNull List<String> activeProfiles = new ArrayList<>();
  private final @NotNull List<String> inactiveProfiles = new ArrayList<>();
  private final @Nullable MavenWorkspaceMap workspaceMap;
  private final boolean updateSnapshots;
  private final Properties userProperties;

  public ProjectResolutionRequest(@NotNull Map<@NotNull File, String> fileToChecksum,
                                  @NotNull Collection<String> activeProfiles,
                                  @NotNull Collection<String> inactiveProfiles,
                                  @Nullable MavenWorkspaceMap workspaceMap,
                                  boolean updateSnapshots,
                                  @NotNull Properties userProperties) {
    this.fileToChecksum = new HashMap<>(fileToChecksum);
    this.activeProfiles.addAll(activeProfiles);
    this.inactiveProfiles.addAll(inactiveProfiles);
    this.workspaceMap = workspaceMap;
    this.updateSnapshots = updateSnapshots;
    this.userProperties = userProperties;
  }

  @NotNull
  public Map<@NotNull File, String> getFileToChecksum() {
    return fileToChecksum;
  }

  @NotNull
  public List<String> getActiveProfiles() {
    return activeProfiles;
  }

  @NotNull
  public List<String> getInactiveProfiles() {
    return inactiveProfiles;
  }

  @Nullable
  public MavenWorkspaceMap getWorkspaceMap() {
    return workspaceMap;
  }

  public boolean updateSnapshots() {
    return updateSnapshots;
  }

  public Properties getUserProperties() {
    return userProperties;
  }
}
