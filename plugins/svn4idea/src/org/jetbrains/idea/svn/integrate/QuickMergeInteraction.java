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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;

import java.util.List;

public interface QuickMergeInteraction {

  @NotNull
  QuickMergeContentsVariants selectMergeVariant();

  boolean shouldContinueSwitchedRootFound();

  boolean shouldReintegrate(@NotNull String targetUrl);

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
