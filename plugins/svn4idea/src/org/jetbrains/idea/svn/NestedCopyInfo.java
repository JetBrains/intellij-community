// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;

public class NestedCopyInfo {
  private final @NotNull VirtualFile myFile;
  private @Nullable Url myUrl;
  private @NotNull WorkingCopyFormat myFormat;
  private final @NotNull NestedCopyType myType;
  private @Nullable Url myRootURL;

  public NestedCopyInfo(final @NotNull VirtualFile file,
                        final @Nullable Url url,
                        final @NotNull WorkingCopyFormat format,
                        final @NotNull NestedCopyType type,
                        @Nullable Url rootURL) {
    myFile = file;
    myUrl = url;
    myFormat = format;
    myType = type;
    myRootURL = rootURL;
  }

  public void setUrl(@Nullable Url url) {
    myUrl = url;
  }

  public @Nullable Url getRootURL() {
    return myRootURL;
  }

  public void setFormat(@NotNull WorkingCopyFormat format) {
    myFormat = format;
  }

  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  public @Nullable Url getUrl() {
    return myUrl;
  }

  public @NotNull WorkingCopyFormat getFormat() {
    return myFormat;
  }

  public @NotNull NestedCopyType getType() {
    return myType;
  }

  private static String key(final VirtualFile file) {
    return file.getPath();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    NestedCopyInfo info = (NestedCopyInfo)o;

    if (!key(myFile).equals(key(info.myFile))) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return key(myFile).hashCode();
  }

  public void setRootURL(final @Nullable Url value) {
    myRootURL = value;
  }
}
