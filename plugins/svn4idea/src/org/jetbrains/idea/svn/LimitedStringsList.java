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

import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LimitedStringsList {
  private static final int ourMaxTypedUrls = 20;
  private final List<String> myList;

  public LimitedStringsList(final List<String> list) {
    myList = new ArrayList<>(ContainerUtil.getFirstItems(list, ourMaxTypedUrls));
  }

  public void add(final String value) {
    myList.remove(value);

    if (myList.size() >= ourMaxTypedUrls) {
      // leave space for 1 to be added
      final int numToRemove = myList.size() - ourMaxTypedUrls + 1;
      myList.subList(0, numToRemove).clear();
    }

    // more recent first
    myList.add(0, value);
  }

  public List<String> getList() {
    return Collections.unmodifiableList(myList);
  }
}
