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
