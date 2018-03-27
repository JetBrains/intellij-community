// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.status.Status;

class SvnChangedFile {

  @NotNull private final FilePath myFilePath;
  @NotNull private final Status myStatus;
  @Nullable private final Url myCopyFromURL;

  public SvnChangedFile(@NotNull FilePath filePath, @NotNull Status status) {
    this(filePath, status, null);
  }

  public SvnChangedFile(@NotNull FilePath filePath, @NotNull Status status, @Nullable Url copyFromURL) {
    myFilePath = filePath;
    myStatus = status;
    myCopyFromURL = copyFromURL;
  }

  @NotNull
  public FilePath getFilePath() {
    return myFilePath;
  }

  @NotNull
  public Status getStatus() {
    return myStatus;
  }

  @Nullable
  public Url getCopyFromURL() {
    return ObjectUtils.chooseNotNull(myCopyFromURL, myStatus.getCopyFromURL());
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
