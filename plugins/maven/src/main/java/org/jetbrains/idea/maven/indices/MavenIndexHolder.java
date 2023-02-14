// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MavenIndexHolder {
  private final @Nullable MavenIndex myLocalIndex;
  private final @NotNull List<MavenIndex> myRemoteIndices;
  private final @NotNull List<MavenIndex> myIndices;

  MavenIndexHolder(@NotNull List<MavenIndex> remoteIndices, @Nullable MavenIndex localIndex) {
    myLocalIndex = localIndex;
    myRemoteIndices = List.copyOf(Objects.requireNonNull(remoteIndices));

    List<MavenIndex> indices = new ArrayList<>(remoteIndices);
    if (myLocalIndex != null) indices.add(myLocalIndex);
    myIndices = List.copyOf(indices);
  }

  public @Nullable MavenIndex getLocalIndex() {
    return myLocalIndex;
  }

  public @NotNull List<MavenIndex> getRemoteIndices() {
    return myRemoteIndices;
  }

  public @NotNull List<MavenIndex> getIndices() {
    return myIndices;
  }

  public boolean isEquals(@NotNull Set<String> remoteUrls, @Nullable String localPath) {
    if (!FileUtilRt.pathsEqual(myLocalIndex != null ? myLocalIndex.getRepositoryPathOrUrl() : null, localPath)) return false;
    if (remoteUrls.size() != myRemoteIndices.size()) return false;
    for (MavenSearchIndex index : myRemoteIndices) {
      if (!remoteUrls.contains(index.getRepositoryPathOrUrl())) return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "MavenIndexHolder{" + myIndices + '}';
  }
}
