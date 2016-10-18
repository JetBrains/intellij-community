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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;

import java.util.List;

public interface QuickMergeInteraction {

  @NotNull
  QuickMergeContentsVariants selectMergeVariant();

  boolean shouldContinueSwitchedRootFound();

  boolean shouldReintegrate(@NotNull String targetUrl);

  @NotNull
  SelectMergeItemsResult selectMergeItems(@NotNull List<CommittedChangeList> lists,
                                          @NotNull String mergeTitle,
                                          @NotNull MergeChecker mergeChecker);

  @NotNull
  LocalChangesAction selectLocalChangesAction(boolean mergeAll);

  void showIntersectedLocalPaths(@NotNull List<FilePath> paths);

  void showErrors(@NotNull String message, @NotNull List<VcsException> exceptions);

  void showErrors(@NotNull String message, boolean isError);

  @NotNull
  List<CommittedChangeList> showRecentListsForSelection(@NotNull List<CommittedChangeList> list,
                                                        @NotNull MergeChecker mergeChecker,
                                                        boolean everyThingLoaded);

  interface SelectMergeItemsResult {
    @NotNull
    QuickMergeContentsVariants getResultCode();

    @NotNull
    List<CommittedChangeList> getSelectedLists();
  }
}
