/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.integrate.LocalChangesAction;
import org.jetbrains.idea.svn.dialogs.MergeDialogI;
import org.jetbrains.idea.svn.integrate.QuickMergeContentsVariants;
import org.jetbrains.idea.svn.integrate.QuickMergeInteraction;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 3/27/13
 * Time: 6:56 PM
 */
public class QuickMergeTestInteraction implements QuickMergeInteraction {
  private QuickMergeContentsVariants myMergeVariant = QuickMergeContentsVariants.all;
  private boolean myReintegrateAnswer = false;
  private LocalChangesAction myLocalChangesAction = LocalChangesAction.continueMerge;
  private QuickMergeContentsVariants mySelectMergeAction2ndStep = QuickMergeContentsVariants.all;

  private final List<Exception> myExceptions;

  public QuickMergeTestInteraction() {
    myExceptions = new ArrayList<>();
  }

  @Override
  public void setTitle(@NotNull String title) {
  }

  @Override
  public QuickMergeContentsVariants selectMergeVariant() {
    return myMergeVariant;
  }

  public void setMergeVariant(QuickMergeContentsVariants mergeVariant) {
    myMergeVariant = mergeVariant;
  }

  @Override
  public boolean shouldContinueSwitchedRootFound() {
    // not gonna test this at the moment
    return false;
  }

  @Override
  public boolean shouldReintegrate(@NotNull String sourceUrl, @NotNull String targetUrl) {
    return myReintegrateAnswer;
  }

  public void setReintegrateAnswer(boolean reintegrateAnswer) {
    myReintegrateAnswer = reintegrateAnswer;
  }

  public void setSelectMergeAction2ndStep(QuickMergeContentsVariants selectMergeAction2ndStep) {
    mySelectMergeAction2ndStep = selectMergeAction2ndStep;
  }

  @NotNull
  @Override
  public SelectMergeItemsResult selectMergeItems(List<CommittedChangeList> lists, String mergeTitle, MergeChecker mergeChecker) {
    return new SelectMergeItemsResult() {
      @Override
      public QuickMergeContentsVariants getResultCode() {
        return mySelectMergeAction2ndStep;
      }

      @Override
      public List<CommittedChangeList> getSelectedLists() {
        return null;
      }
    };
  }

  @Override
  public List<CommittedChangeList> showRecentListsForSelection(@NotNull List<CommittedChangeList> list,
                                                               @NotNull String mergeTitle,
                                                               @NotNull MergeChecker mergeChecker,
                                                               @NotNull PairConsumer<Long, MergeDialogI> loader,
                                                               boolean everyThingLoaded) {
    return null;
  }

  @NotNull
  @Override
  public LocalChangesAction selectLocalChangesAction(boolean mergeAll) {
    return myLocalChangesAction;
  }

  public void setLocalChangesAction(LocalChangesAction localChangesAction) {
    myLocalChangesAction = localChangesAction;
  }

  @Override
  public void showIntersectedLocalPaths(List<FilePath> paths) {
  }

  @Override
  public void showError(@NotNull Exception exception) {
    myExceptions.add(exception);
  }

  @Override
  public void showErrors(String message, List<VcsException> exceptions) {
    if (exceptions != null && ! exceptions.isEmpty()) {
      myExceptions.addAll(exceptions);
      return;
    }
    myExceptions.add(new RuntimeException(message));
  }

  @Override
  public void showErrors(String message, boolean isError) {
    if (isError) {
      myExceptions.add(new RuntimeException(message));
    } else {
      System.out.println("merge warning: " + message);
    }
  }

  public void throwIfExceptions() throws Exception {
    for (Exception exception : myExceptions) {
      throw exception;
    }
  }
}
