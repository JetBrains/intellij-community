/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.vcs.CollectionSplitter;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;

import java.util.List;

public class SvnChangeListsPageEngine implements PageEngine<List<CommittedChangeList>> {
  private List<List<CommittedChangeList>> mySplitData;
  private int myIdx;

  public SvnChangeListsPageEngine(final List<CommittedChangeList> data, final int pageSize) {
    final CollectionSplitter<CommittedChangeList> splitter = new CollectionSplitter<CommittedChangeList>(pageSize);
    mySplitData = splitter.split(data);
    myIdx = 0;
  }

  public List<CommittedChangeList> getCurrent() {
    return mySplitData.get(myIdx);
  }

  public boolean hasNext() {
    return myIdx < (mySplitData.size() - 1);
  }

  public boolean hasPrevious() {
    return myIdx > 0;
  }

  public List<CommittedChangeList> next() {
    if (! hasNext()) return null;
    ++ myIdx;
    return mySplitData.get(myIdx);
  }

  public List<CommittedChangeList> previous() {
    if (! hasPrevious()) return null;
    -- myIdx;
    return mySplitData.get(myIdx);
  }
}
