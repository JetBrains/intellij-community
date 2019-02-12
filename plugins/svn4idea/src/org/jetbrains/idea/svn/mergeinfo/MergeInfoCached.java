// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.mergeinfo;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class MergeInfoCached {

  @NotNull private final Map<Long, MergeCheckResult> myMap;
  private final long myCopyRevision;

  public MergeInfoCached() {
    myMap = ContainerUtil.newHashMap();
    myCopyRevision = -1;
  }

  public MergeInfoCached(@NotNull Map<Long, MergeCheckResult> map, long copyRevision) {
    myMap = ContainerUtil.newHashMap(map);
    myCopyRevision = copyRevision;
  }

  @NotNull
  public Map<Long, MergeCheckResult> getMap() {
    return myMap;
  }

  @NotNull
  public MergeInfoCached copy() {
    return new MergeInfoCached(myMap, myCopyRevision);
  }

  public boolean copiedAfter(@NotNull CommittedChangeList list) {
    return myCopyRevision != -1 && myCopyRevision >= list.getNumber();
  }
}
