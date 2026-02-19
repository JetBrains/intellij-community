// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenWorkspaceMap;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

public class ProjectResolutionRequest implements Serializable {
  private final @NotNull List<@NotNull File> filesToResolve;
  private final @NotNull PomHashMap pomHashMap;
  private final @NotNull List<String> activeProfiles = new ArrayList<>();
  private final @NotNull List<String> inactiveProfiles = new ArrayList<>();
  private final @Nullable MavenWorkspaceMap workspaceMap;
  private final boolean updateSnapshots;
  private final Properties userProperties;

  public ProjectResolutionRequest(@NotNull List<@NotNull File> filesToResolve,
                                  @NotNull PomHashMap pomHashMap,
                                  @NotNull Collection<String> activeProfiles,
                                  @NotNull Collection<String> inactiveProfiles,
                                  @Nullable MavenWorkspaceMap workspaceMap,
                                  boolean updateSnapshots,
                                  @NotNull Properties userProperties) {
    this.filesToResolve = filesToResolve;
    this.pomHashMap = pomHashMap;
    this.activeProfiles.addAll(activeProfiles);
    this.inactiveProfiles.addAll(inactiveProfiles);
    this.workspaceMap = workspaceMap;
    this.updateSnapshots = updateSnapshots;
    this.userProperties = userProperties;
  }

  public @NotNull List<@NotNull File> getFilesToResolve() {
    return filesToResolve;
  }

  public @NotNull PomHashMap getPomHashMap() {
    return pomHashMap;
  }

  public @NotNull List<String> getActiveProfiles() {
    return activeProfiles;
  }

  public @NotNull List<String> getInactiveProfiles() {
    return inactiveProfiles;
  }

  public @Nullable MavenWorkspaceMap getWorkspaceMap() {
    return workspaceMap;
  }

  public boolean updateSnapshots() {
    return updateSnapshots;
  }

  public Properties getUserProperties() {
    return userProperties;
  }
}
