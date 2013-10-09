/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNURL;

/**
 * @author Konstantin Kolosovsky.
 */
public class NestedCopyInfo {
  @NotNull private final VirtualFile myFile;
  @Nullable private SVNURL myUrl;
  @NotNull private WorkingCopyFormat myFormat;
  @NotNull private final NestedCopyType myType;
  @Nullable private SVNURL myRootURL;

  public NestedCopyInfo(@NotNull final VirtualFile file,
                        @Nullable final SVNURL url,
                        @NotNull final WorkingCopyFormat format,
                        @NotNull final NestedCopyType type,
                        @Nullable SVNURL rootURL) {
    myFile = file;
    myUrl = url;
    myFormat = format;
    myType = type;
    myRootURL = rootURL;
  }

  public void setUrl(@Nullable SVNURL url) {
    myUrl = url;
  }

  @Nullable
  public SVNURL getRootURL() {
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
  public SVNURL getUrl() {
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

  public void setRootURL(@Nullable final SVNURL value) {
    myRootURL = value;
  }
}
