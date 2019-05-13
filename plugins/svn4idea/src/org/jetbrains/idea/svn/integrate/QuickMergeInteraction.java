// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;

import java.util.List;

public interface QuickMergeInteraction {

  @NotNull
  QuickMergeContentsVariants selectMergeVariant();

  boolean shouldContinueSwitchedRootFound();

  boolean shouldReintegrate(@NotNull Url targetUrl);

  @NotNull
  SelectMergeItemsResult selectMergeItems(@NotNull List<SvnChangeList> lists,
                                          @NotNull MergeChecker mergeChecker,
                                          boolean allStatusesCalculated,
                                          boolean allListsLoaded);

  @NotNull
  LocalChangesAction selectLocalChangesAction(boolean mergeAll);

  void showIntersectedLocalPaths(@NotNull List<FilePath> paths);

  void showErrors(@NotNull String message, @NotNull List<VcsException> exceptions);

  void showErrors(@NotNull String message, boolean isError);
}
