// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;

import java.io.File;

public class WorkingCopy {
  private final boolean myIs17Copy;
  @NotNull private final File myFile;
  @NotNull private final Url myUrl;

  // TODO: is17Copy is currently true in all constructor usages - remove this field and revise is17Copy() usages
  public WorkingCopy(@NotNull File file, @NotNull Url url, boolean is17Copy) {
    myFile = file;
    myUrl = url;
    myIs17Copy = is17Copy;
  }

  @NotNull
  public File getFile() {
    return myFile;
  }

  @NotNull
  public Url getUrl() {
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
