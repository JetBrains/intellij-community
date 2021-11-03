// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

public class MavenIndexId implements Serializable {
  public final @NotNull String indexId;
  public final @NotNull String repositoryId;
  public final @Nullable String repositoryFilePath;
  public final @Nullable String url;
  public final @NotNull String indexDirPath;

  public MavenIndexId(@NotNull String indexId,
                      @NotNull String repositoryId,
                      @Nullable String repositoryFilePath,
                      @Nullable String url,
                      @NotNull String indexDirPath) {
    this.indexId = indexId;
    this.repositoryId = repositoryId;
    this.repositoryFilePath = repositoryFilePath;
    this.url = url;
    this.indexDirPath = indexDirPath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenIndexId id = (MavenIndexId)o;

    if (!indexId.equals(id.indexId)) return false;
    if (!repositoryId.equals(id.repositoryId)) return false;
    if (repositoryFilePath != null ? !repositoryFilePath.equals(id.repositoryFilePath) : id.repositoryFilePath != null) return false;
    if (url != null ? !url.equals(id.url) : id.url != null) return false;
    if (!indexDirPath.equals(id.indexDirPath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = indexId.hashCode();
    result = 31 * result + repositoryId.hashCode();
    result = 31 * result + (repositoryFilePath != null ? repositoryFilePath.hashCode() : 0);
    result = 31 * result + (url != null ? url.hashCode() : 0);
    result = 31 * result + indexDirPath.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "MavenIndexId{" +
           "indexId='" + indexId + '\'' +
           ", repositoryId='" + repositoryId + '\'' +
           ", repositoryFilePath='" + repositoryFilePath + '\'' +
           ", url='" + url + '\'' +
           ", indexDirPath='" + indexDirPath + '\'' +
           '}';
  }
}
