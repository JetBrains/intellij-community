// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ProjectResolutionRequest implements Serializable {
  private final @NotNull List<File> pomFiles;
  private final @NotNull List<String> activeProfiles = new ArrayList<>();
  private final @NotNull List<String> inactiveProfiles = new ArrayList<>();

  public ProjectResolutionRequest(@NotNull List<File> pomFiles,
                                  @NotNull Collection<String> activeProfiles,
                                  @NotNull Collection<String> inactiveProfiles) {
    this.pomFiles = pomFiles;
    this.activeProfiles.addAll(activeProfiles);
    this.inactiveProfiles.addAll(inactiveProfiles);
  }

  @NotNull
  public List<File> getPomFiles() {
    return pomFiles;
  }

  @NotNull
  public List<String> getActiveProfiles() {
    return activeProfiles;
  }

  @NotNull
  public List<String> getInactiveProfiles() {
    return inactiveProfiles;
  }
}
