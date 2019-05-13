/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.history.SvnChangeList;

import java.util.List;

public class SelectMergeItemsResult {
  @NotNull private final QuickMergeContentsVariants myResultCode;
  @NotNull private final List<SvnChangeList> myLists;

  public SelectMergeItemsResult(@NotNull QuickMergeContentsVariants resultCode, @NotNull List<SvnChangeList> lists) {
    myResultCode = resultCode;
    myLists = lists;
  }

  @NotNull
  public QuickMergeContentsVariants getResultCode() {
    return myResultCode;
  }

  @NotNull
  public List<SvnChangeList> getSelectedLists() {
    return myLists;
  }
}
