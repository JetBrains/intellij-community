// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;

/**
 * @author Konstantin Kolosovsky.
 */
public class NestedCopyInfo {
  @NotNull private final VirtualFile myFile;
  @Nullable private Url myUrl;
  @NotNull private WorkingCopyFormat myFormat;
  @NotNull private final NestedCopyType myType;
  @Nullable private Url myRootURL;

  public NestedCopyInfo(@NotNull final VirtualFile file,
                        @Nullable final Url url,
                        @NotNull final WorkingCopyFormat format,
                        @NotNull final NestedCopyType type,
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

  @Nullable
  public Url getRootURL() {
    return myRootURL;
  }

  public void setFormat(@NotNull WorkingCopyFormat format) {
    myFormat = format;
  }

  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @Nullable
  public Url getUrl() {
    return myUrl;
  }

  @NotNull
  public WorkingCopyFormat getFormat() {
    return myFormat;
  }

  @NotNull
  public NestedCopyType getType() {
    return myType;
  }

  private String key(final VirtualFile file) {
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

  public void setRootURL(@Nullable final Url value) {
    myRootURL = value;
  }
}
