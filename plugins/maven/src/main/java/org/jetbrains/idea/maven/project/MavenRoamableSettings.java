// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MavenRoamableSettings {
  public final List<String> enabledProfiles;
  public final List<String> disabledProfiles;
  public final List<String> originalFiles;
  public final List<String> ignoredFiles;
  public final List<String> ignoredPathMasks;

  public MavenRoamableSettings(@NotNull List<String> enabledProfiles,
                               @NotNull List<String> disabledProfiles,
                               @NotNull List<String> originalFiles,
                               @NotNull List<String> ignoredFiles,
                               @NotNull List<String> ignoredPathMasks) {
    this.enabledProfiles = enabledProfiles;
    this.disabledProfiles = disabledProfiles;
    this.originalFiles = originalFiles;
    this.ignoredFiles = ignoredFiles;
    this.ignoredPathMasks = ignoredPathMasks;
  }
}
