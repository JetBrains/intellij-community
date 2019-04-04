// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.integrate.LocalChangesAction;
import org.jetbrains.idea.svn.integrate.QuickMergeContentsVariants;
import org.jetbrains.idea.svn.integrate.QuickMergeInteraction;
import org.jetbrains.idea.svn.integrate.SelectMergeItemsResult;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.isEmpty;
import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static com.intellij.util.containers.ContainerUtilRt.emptyList;

public class QuickMergeTestInteraction implements QuickMergeInteraction {

  private QuickMergeContentsVariants myMergeVariant = QuickMergeContentsVariants.all;
  private final boolean myReintegrateAnswer;
  @Nullable private final Function.Mono<List<SvnChangeList>> mySelectedListsProvider;
  @NotNull private final List<Exception> myExceptions;

  public QuickMergeTestInteraction(boolean reintegrate, @Nullable Function.Mono<List<SvnChangeList>> selectedListsProvider) {
    myReintegrateAnswer = reintegrate;
    mySelectedListsProvider = selectedListsProvider;
    myExceptions = newArrayList();
  }

  @NotNull
  @Override
  public QuickMergeContentsVariants selectMergeVariant() {
    return myMergeVariant;
  }

  public void setMergeVariant(@NotNull QuickMergeContentsVariants mergeVariant) {
    myMergeVariant = mergeVariant;
  }

  @Override
  public boolean shouldContinueSwitchedRootFound() {
    return false;
  }

  @Override
  public boolean shouldReintegrate(@NotNull Url targetUrl) {
    return myReintegrateAnswer;
  }

  @NotNull
  @Override
  public SelectMergeItemsResult selectMergeItems(@NotNull List<SvnChangeList> lists,
                                                 @NotNull MergeChecker mergeChecker,
                                                 boolean allStatusesCalculated,
                                                 boolean allListsLoaded) {
    return new SelectMergeItemsResult(
      mySelectedListsProvider != null ? QuickMergeContentsVariants.select : QuickMergeContentsVariants.all,
      mySelectedListsProvider != null ? mySelectedListsProvider.fun(lists) : emptyList()
    );
  }

  @NotNull
  @Override
  public LocalChangesAction selectLocalChangesAction(boolean mergeAll) {
    return LocalChangesAction.continueMerge;
  }

  @Override
  public void showIntersectedLocalPaths(@NotNull List<FilePath> paths) {
  }

  @Override
  public void showErrors(@NotNull String message, @NotNull List<VcsException> exceptions) {
    if (!isEmpty(exceptions)) {
      myExceptions.addAll(exceptions);
    }
    else {
      myExceptions.add(new RuntimeException(message));
    }
  }

  @Override
  public void showErrors(@NotNull String message, boolean isError) {
    if (isError) {
      myExceptions.add(new RuntimeException(message));
    } else {
      System.out.println("merge warning: " + message);
    }
  }

  public void throwIfExceptions() throws Exception {
    if (!myExceptions.isEmpty()) {
      throw myExceptions.get(0);
    }
  }
}
