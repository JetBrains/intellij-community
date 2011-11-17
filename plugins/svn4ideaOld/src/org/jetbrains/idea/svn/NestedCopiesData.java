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

import com.intellij.util.Consumer;

import java.util.HashSet;
import java.util.Set;

public class NestedCopiesData implements Consumer<Set<NestedCopiesBuilder.MyPointInfo>> {
  // we can keep the type here also, but 
  private final Set<NestedCopiesBuilder.MyPointInfo> mySet;

  public NestedCopiesData() {
    mySet = new HashSet<NestedCopiesBuilder.MyPointInfo>();
  }

  public void consume(final Set<NestedCopiesBuilder.MyPointInfo> nestedCopyTypeSet) {
    mySet.addAll(nestedCopyTypeSet);
  }

  public Set<NestedCopiesBuilder.MyPointInfo> getSet() {
    return mySet;
  }
}
