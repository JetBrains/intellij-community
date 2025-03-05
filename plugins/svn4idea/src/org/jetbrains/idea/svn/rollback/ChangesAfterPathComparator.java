// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.rollback;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;

import java.util.Comparator;

public class ChangesAfterPathComparator implements Comparator<Change> {
  private static final ChangesAfterPathComparator ourInstance = new ChangesAfterPathComparator();
  private static final Comparator<ContentRevision> ourComparator =
    (o1, o2) -> FileUtil.compareFiles(o1.getFile().getIOFile(), o2.getFile().getIOFile());

  public static ChangesAfterPathComparator getInstance() {
    return ourInstance;
  }

  @Override
  public int compare(Change o1, Change o2) {
    final ContentRevision ar1 = o1.getAfterRevision();
    final ContentRevision ar2 = o2.getAfterRevision();
    return Comparing.compare(ar1, ar2, ourComparator);
  }
}
