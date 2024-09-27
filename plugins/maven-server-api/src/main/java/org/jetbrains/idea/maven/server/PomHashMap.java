// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.*;

public class PomHashMap implements Serializable {
  @NotNull
  private final Map<@NotNull File, @NotNull PomHashValue> pomMap = new HashMap<>();

  @NotNull
  private final Map<@NotNull File, @NotNull HashSet<@NotNull File>> pomDependencies = new HashMap<>();

  public void put(@NotNull File pom, @Nullable String dependencyHash) {
    pomMap.put(pom, new PomHashValue(dependencyHash));
  }

  public void addFileDependency(@NotNull File pom, @NotNull File dependentPom) {
    pomDependencies.putIfAbsent(pom, new HashSet<>());
    pomDependencies.get(pom).add(dependentPom);
  }

  public void addFileDependencies(@NotNull File pom, @NotNull Collection<@NotNull File> dependentPoms) {
    for (File dependentPom : dependentPoms) {
      addFileDependency(pom, dependentPom);
    }
  }

  @NotNull
  public Set<@NotNull File> getFileDependencies(@NotNull File pom) {
    Set<@NotNull File> dependencies = pomDependencies.get(pom);
    return dependencies == null ? Collections.emptySet() : dependencies;
  }

  @NotNull
  public Set<@NotNull File> keySet() {
    return pomMap.keySet();
  }

  @Nullable
  public String getDependencyHash(@NotNull File file) {
    PomHashValue data = pomMap.get(file);
    return null == data ? null : data.getDependencyHash();
  }

  public boolean isEmpty() {
    return pomMap.isEmpty();
  }

  public int size() {
    return pomMap.size();
  }

  @Override
  public String toString() {
    return pomMap.toString();
  }
}

class PomHashValue implements Serializable {
  @Nullable
  private final String dependencyHash;

  PomHashValue(@Nullable String dependencyHash) {
    this.dependencyHash = dependencyHash;
  }

  @Nullable
  public String getDependencyHash() {
    return dependencyHash;
  }

  @Override
  public String toString() {
    return "dependencyHash=" + dependencyHash;
  }
}
