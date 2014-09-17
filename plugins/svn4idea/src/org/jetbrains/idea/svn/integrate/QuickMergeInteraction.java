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
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.MergeDialogI;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 3/26/13
 * Time: 8:29 PM
 */
public interface QuickMergeInteraction {
  void setTitle(@NotNull final String title);
  QuickMergeContentsVariants selectMergeVariant();
  boolean shouldContinueSwitchedRootFound();

  boolean shouldReintegrate(@NotNull final String sourceUrl, @NotNull final String targetUrl);

  @NotNull
  SelectMergeItemsResult selectMergeItems(final List<CommittedChangeList> lists, final String mergeTitle, final MergeChecker mergeChecker);

  @NotNull
  LocalChangesAction selectLocalChangesAction(boolean mergeAll);

  void showIntersectedLocalPaths(final List<FilePath> paths);

  void showError(@NotNull Exception exception);
  void showErrors(final String message, final List<VcsException> exceptions);
  void showErrors(final String message, final boolean isError);

  List<CommittedChangeList> showRecentListsForSelection(@NotNull List<CommittedChangeList> list,
                                                        @NotNull String mergeTitle,
                                                        @NotNull MergeChecker mergeChecker,
                                                        @NotNull PairConsumer<Long, MergeDialogI> loader, boolean everyThingLoaded);

  interface SelectMergeItemsResult {
    QuickMergeContentsVariants getResultCode();
    List<CommittedChangeList> getSelectedLists();
  }
}
