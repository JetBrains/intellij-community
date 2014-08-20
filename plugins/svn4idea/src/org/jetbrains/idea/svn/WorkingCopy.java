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

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;

public class WorkingCopy {
  private final boolean myIs17Copy;
  @NotNull private final File myFile;
  @NotNull private final SVNURL myUrl;

  // TODO: is17Copy is currently true in all constructor usages - remove this field and revise is17Copy() usages
  public WorkingCopy(@NotNull File file, @NotNull SVNURL url, boolean is17Copy) {
    myFile = file;
    myUrl = url;
    myIs17Copy = is17Copy;
  }

  @NotNull
  public File getFile() {
    return myFile;
  }

  @NotNull
  public SVNURL getUrl() {
    return myUrl;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    WorkingCopy that = (WorkingCopy)o;

    if (!myFile.equals(that.myFile)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myFile.hashCode();
  }

  public boolean is17Copy() {
    return myIs17Copy;
  }
}
