// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.status.Status;

class SvnChangedFile {

  private final @NotNull FilePath myFilePath;
  private final @NotNull Status myStatus;
  private final @Nullable Url myCopyFromURL;

  SvnChangedFile(@NotNull FilePath filePath, @NotNull Status status) {
    this(filePath, status, null);
  }

  SvnChangedFile(@NotNull FilePath filePath, @NotNull Status status, @Nullable Url copyFromURL) {
    myFilePath = filePath;
    myStatus = status;
    myCopyFromURL = copyFromURL;
  }

  public @NotNull FilePath getFilePath() {
    return myFilePath;
  }

  public @NotNull Status getStatus() {
    return myStatus;
  }

  public @Nullable Url getCopyFromURL() {
    return ObjectUtils.chooseNotNull(myCopyFromURL, myStatus.getCopyFromUrl());
  }

  @Override
  public String toString() {
    return myFilePath.getPath();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final SvnChangedFile that = (SvnChangedFile)o;

    return myFilePath.equals(that.myFilePath);
  }

  @Override
  public int hashCode() {
    return myFilePath.hashCode();
  }
}
