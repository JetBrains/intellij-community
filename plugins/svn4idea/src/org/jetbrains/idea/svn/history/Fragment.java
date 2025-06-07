// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private final @Nullable ChangesBunch myOriginBunch;

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

  public @Nullable ChangesBunch getOriginBunch() {
    return myOriginBunch;
  }

  public boolean isConsistentWithOlder() {
    return myConsistentWithOlder;
  }

  public boolean isConsistentWithYounger() {
    return myConsistentWithYounger;
  }
}
