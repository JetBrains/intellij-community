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
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;

import java.util.LinkedList;
import java.util.List;

public class GroupSplitter extends Splitter {
  private final List<Integer> myList;
  private int myCnt;

  public GroupSplitter(final List<CommittedChangeList> lists) {
    super(lists);

    myCnt = 0;
    myList = new LinkedList<Integer>();
    if (lists.isEmpty()) return;

    int currentPackSize = 1;
    for (int i = 1; i < lists.size(); i++) {
      final CommittedChangeList list = lists.get(i);
      if (list.getNumber() != (lists.get(i - 1).getNumber() + 1)) {
        myList.add(currentPackSize);
        currentPackSize = 1;
      } else {
        ++ currentPackSize;
      }
    }
    myList.add(currentPackSize);
  }

  @Override
  public boolean hasNext() {
    return myCnt < myList.size();
  }

  @Override
  public int step() {
    ++ myCnt;
    return myList.get(myCnt - 1);
  }
}
