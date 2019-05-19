// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;

import java.util.Comparator;

public class ByNumberChangeListComparator implements Comparator<CommittedChangeList> {
  private final static ByNumberChangeListComparator ourInstance = new ByNumberChangeListComparator();

  public static ByNumberChangeListComparator getInstance() {
    return ourInstance;
  }

  @Override
  public int compare(final CommittedChangeList o1, final CommittedChangeList o2) {
    return (int) (o1.getNumber() - o2.getNumber());
  }
}
