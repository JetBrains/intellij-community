// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SvnMappingSavedPart {
  public List<SvnCopyRootSimple> myMappingRoots = new ArrayList<>();
  public List<SvnCopyRootSimple> myMoreRealMappingRoots = new ArrayList<>();

  public void add(@NotNull RootUrlInfo info) {
    myMappingRoots.add(new SvnCopyRootSimple(info));
  }

  public void addReal(@NotNull RootUrlInfo info) {
    myMoreRealMappingRoots.add(new SvnCopyRootSimple(info));
  }

  public List<SvnCopyRootSimple> getMappingRoots() {
    return myMappingRoots;
  }

  public List<SvnCopyRootSimple> getMoreRealMappingRoots() {
    return myMoreRealMappingRoots;
  }
}
