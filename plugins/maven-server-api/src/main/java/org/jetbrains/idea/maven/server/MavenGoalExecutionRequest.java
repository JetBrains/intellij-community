// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;

import java.io.File;
import java.io.Serializable;
import java.util.Objects;
import java.util.Properties;

public final class MavenGoalExecutionRequest implements Serializable {
  @NotNull private final File file;
  @NotNull private final MavenExplicitProfiles profiles;
  @NotNull private final Properties userProperties;

  public MavenGoalExecutionRequest(@NotNull File file, @NotNull MavenExplicitProfiles profiles)  {
    this(file, profiles, new Properties());
  }

  public MavenGoalExecutionRequest(@NotNull File file, @NotNull MavenExplicitProfiles profiles, @NotNull Properties userProperties) {
    this.file = file;
    this.profiles = profiles;
    this.userProperties = userProperties;
  }

  public File file() { return file; }

  public MavenExplicitProfiles profiles() { return profiles; }

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
           Objects.equals(this.userProperties, that.userProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(file, profiles);
  }

  @Override
  public String toString() {
    return "MavenExecutionRequest[" +
           "file=" + file + ", " +
           "profiles=" + profiles + ']';
  }
}