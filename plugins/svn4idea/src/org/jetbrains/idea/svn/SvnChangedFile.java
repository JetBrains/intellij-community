/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.status.Status;

class SvnChangedFile {

  @NotNull private final FilePath myFilePath;
  @NotNull private final Status myStatus;
  @Nullable private final String myCopyFromURL;

  public SvnChangedFile(@NotNull FilePath filePath, @NotNull Status status) {
    this(filePath, status, null);
  }

  public SvnChangedFile(@NotNull FilePath filePath, @NotNull Status status, @Nullable String copyFromURL) {
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
  public String getCopyFromURL() {
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
