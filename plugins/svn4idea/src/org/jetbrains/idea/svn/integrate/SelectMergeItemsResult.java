// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.integrate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.history.SvnChangeList;

import java.util.List;

public class SelectMergeItemsResult {
  private final @NotNull QuickMergeContentsVariants myResultCode;
  private final @NotNull List<SvnChangeList> myLists;

  public SelectMergeItemsResult(@NotNull QuickMergeContentsVariants resultCode, @NotNull List<SvnChangeList> lists) {
    myResultCode = resultCode;
    myLists = lists;
  }

  public @NotNull QuickMergeContentsVariants getResultCode() {
    return myResultCode;
  }

  public @NotNull List<SvnChangeList> getSelectedLists() {
    return myLists;
  }
}
