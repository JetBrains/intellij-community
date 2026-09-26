// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public final class MavenGoalExecutionRequest implements Serializable {
  private final @NotNull File file;
  private final @NotNull MavenExplicitProfiles profiles;
  private final List<String> selectedProjects;
  private final @NotNull Properties userProperties;

  public MavenGoalExecutionRequest(@NotNull File file, @NotNull MavenExplicitProfiles profiles)  {
    this(file, profiles, Collections.emptyList(), new Properties());
  }

  public MavenGoalExecutionRequest(
    @NotNull File file,
    @NotNull MavenExplicitProfiles profiles,
    @NotNull List<@NotNull String> selectedProjects,
    @NotNull Properties userProperties) {
    this.file = file;
    this.profiles = profiles;
    this.selectedProjects = selectedProjects;
    this.userProperties = userProperties;
  }

  public File file() { return file; }

  public MavenExplicitProfiles profiles() { return profiles; }

  public @NotNull List<@NotNull String> selectedProjects() {
    return selectedProjects;
  }

  public @NotNull Properties userProperties() {
    return userProperties;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    MavenGoalExecutionRequest that = (MavenGoalExecutionRequest)obj;
    return Objects.equals(this.file, that.file) &&
           Objects.equals(this.profiles, that.profiles) &&
           Objects.equals(this.selectedProjects, that.selectedProjects) &&
           Objects.equals(this.userProperties, that.userProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(file, profiles, selectedProjects, userProperties);
  }

  @Override
  public String toString() {
    return "MavenExecutionRequest[" +
           "file=" + file +
           ", profiles=" + profiles +
           ", selectedProjects=" + selectedProjects + ']';
  }
}