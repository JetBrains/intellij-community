// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;

import java.io.File;
import java.io.Serializable;
import java.util.Objects;

public final class MavenGoalExecutionRequest implements Serializable {
  @NotNull private final File file;
  @NotNull private final MavenExplicitProfiles profiles;

  public MavenGoalExecutionRequest(@NotNull File file, @NotNull MavenExplicitProfiles profiles) {
    this.file = file;
    this.profiles = profiles;
  }

  public File file() { return file; }

  public MavenExplicitProfiles profiles() { return profiles; }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    MavenGoalExecutionRequest that = (MavenGoalExecutionRequest)obj;
    return Objects.equals(this.file, that.file) &&
           Objects.equals(this.profiles, that.profiles);
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