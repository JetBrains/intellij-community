// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PomHashMap implements Serializable {
  @NotNull
  private final Map<@NotNull File, @NotNull PomHashValue> pomMap = new HashMap<>();

  public void put(@NotNull File pom, @Nullable String dependencyHash) {
    pomMap.put(pom, new PomHashValue(dependencyHash));
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
