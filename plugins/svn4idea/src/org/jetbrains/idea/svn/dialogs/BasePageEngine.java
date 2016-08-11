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

import java.util.List;

public class BasePageEngine<T> implements PageEngine<List<T>> {
  private List<List<T>> mySplitData;
  private int myIdx;

  public BasePageEngine(final List<T> data, final int pageSize) {
    final CollectionSplitter<T> splitter = new CollectionSplitter<>(pageSize);
    mySplitData = splitter.split(data);
    myIdx = 0;
  }

  public List<T> getCurrent() {
    return mySplitData.get(myIdx);
  }

  public boolean hasNext() {
    return myIdx < (mySplitData.size() - 1);
  }

  public boolean hasPrevious() {
    return myIdx > 0;
  }

  public List<T> next() {
    if (! hasNext()) return null;
    ++ myIdx;
    return mySplitData.get(myIdx);
  }

  public List<T> previous() {
    if (! hasPrevious()) return null;
    -- myIdx;
    return mySplitData.get(myIdx);
  }
}
