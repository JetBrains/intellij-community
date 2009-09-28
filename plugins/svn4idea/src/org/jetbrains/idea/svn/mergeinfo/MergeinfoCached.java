package org.jetbrains.idea.svn.mergeinfo;

import java.util.HashMap;
import java.util.Map;

public class MergeinfoCached {
  private final Map<Long, SvnMergeInfoCache.MergeCheckResult> myMap;
  private final long myCopyRevision;

  public MergeinfoCached() {
    myMap = new HashMap<Long, SvnMergeInfoCache.MergeCheckResult>();
    myCopyRevision = -1;
  }

  public MergeinfoCached(final Map<Long, SvnMergeInfoCache.MergeCheckResult> map, final long copyRevision) {
    myMap = map;
    myCopyRevision = copyRevision;
  }

  public Map<Long, SvnMergeInfoCache.MergeCheckResult> getMap() {
    return myMap;
  }

  public long getCopyRevision() {
    return myCopyRevision;
  }
}
