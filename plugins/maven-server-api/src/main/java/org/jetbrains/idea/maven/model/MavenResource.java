// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.model;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MavenResource implements Serializable {
  private final @NotNull String myDirectory;
  private final boolean myFiltered;
  private final String myTargetPath;
  private final List<String> myIncludes;
  private final List<String> myExcludes;

  public MavenResource(@NotNull String directory, boolean filtered, String targetPath, List<String> includes, List<String> excludes) {
    myDirectory = directory;
    myFiltered = filtered;
    myTargetPath = targetPath;
    myIncludes = includes == null ? Collections.emptyList() : new ArrayList<String>(includes);
    myExcludes = excludes == null ? Collections.emptyList() : new ArrayList<String>(excludes);
  }

  public MavenResource(@NotNull MavenSource source) {
    this(source.getDirectory(), source.isFiltered(), source.getTargetPath(), source.getIncludes(), source.getExcludes());
  }

  public @NotNull String getDirectory() {
    return myDirectory;
  }

  public boolean isFiltered() {
    return myFiltered;
  }

  public String getTargetPath() {
    return myTargetPath;
  }

  public List<String> getIncludes() {
    return myIncludes;
  }

  public List<String> getExcludes() {
    return myExcludes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenResource that = (MavenResource)o;

    if (myFiltered != that.myFiltered) return false;
    if (!Objects.equals(myDirectory, that.myDirectory)) return false;
    if (!myExcludes.equals(that.myExcludes)) return false;
    if (!myIncludes.equals(that.myIncludes)) return false;
    if (!Objects.equals(myTargetPath, that.myTargetPath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myDirectory.hashCode();
    result = 31 * result + (myFiltered ? 1 : 0);
    result = 31 * result + (myTargetPath != null ? myTargetPath.hashCode() : 0);
    result = 31 * result + myIncludes.hashCode();
    result = 31 * result + myExcludes.hashCode();
    return result;
  }
}
