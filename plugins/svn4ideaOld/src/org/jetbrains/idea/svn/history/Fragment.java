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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.changes.committed.ChangesBunch;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class Fragment {
  private final Origin myOrigin;
  private final List<CommittedChangeList> myList;
  private final boolean myConsistentWithOlder;
  private final boolean myConsistentWithYounger;
  @Nullable
  private final ChangesBunch myOriginBunch;

  public Fragment(final Origin origin, final List<CommittedChangeList> list, final boolean consistentWithOlder,
                   final boolean consistentWithYounger, final ChangesBunch originBunch) {
    myOrigin = origin;
    myList = list;
    myConsistentWithOlder = consistentWithOlder;
    myConsistentWithYounger = consistentWithYounger;
    myOriginBunch = originBunch;
  }

  public Origin getOrigin() {
    return myOrigin;
  }

  public List<CommittedChangeList> getList() {
    return myList;
  }

  @Nullable
  public ChangesBunch getOriginBunch() {
    return myOriginBunch;
  }

  public boolean isConsistentWithOlder() {
    return myConsistentWithOlder;
  }

  public boolean isConsistentWithYounger() {
    return myConsistentWithYounger;
  }
}
