/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.actions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.integrate.ChangeSetMergerFactory;
import org.jetbrains.idea.svn.integrate.MergerFactory;
import org.jetbrains.idea.svn.integrate.SelectedChangeSetChecker;
import org.jetbrains.idea.svn.integrate.SelectedCommittedStuffChecker;

public class IntegrateChangeSetAction extends AbstractIntegrateChangesAction<SelectedChangeSetChecker> {
  public IntegrateChangeSetAction() {
    super(true);
  }

  @NotNull
  protected MergerFactory createMergerFactory(SelectedChangeSetChecker checker) {
    return new ChangeSetMergerFactory(checker.getSelectedLists().get(0), checker.getSelectedChanges());
  }

  @NotNull
  protected SelectedChangeSetChecker createChecker() {
    return new SelectedChangeSetChecker();
  }

  protected String getSelectedBranchUrl(SelectedCommittedStuffChecker checker) {
    return null;
  }

  protected String getSelectedBranchLocalPath(SelectedCommittedStuffChecker checker) {
    return null;
  }

  protected String getDialogTitle() {
    return null;
  }
}
