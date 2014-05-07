/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.ContinuationPause;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.MergeContext;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

/**
 * @author Konstantin Kolosovsky.
 */
public class MergeTask extends BaseMergeTask {
  private final MergerFactory myFactory;

  // awt since only a wrapper
  public MergeTask(@NotNull MergeContext mergeContext,
                   @NotNull QuickMergeInteraction interaction, final MergerFactory factory,
                   final String mergeTitle) {
    super(mergeContext, interaction, mergeTitle, Where.AWT);
    myFactory = factory;
  }

  @Override
  public void run(ContinuationContext context) {
    final SVNURL sourceUrlUrl;
    try {
      sourceUrlUrl = SVNURL.parseURIEncoded(myMergeContext.getSourceUrl());
    }
    catch (SVNException e) {
      finishWithError(context, "Cannot merge: " + e.getMessage(), true);
      return;
    }

    final SvnIntegrateChangesTask task = new SvnIntegrateChangesTask(myMergeContext.getVcs(),
                                                                     new WorkingCopyInfo(myMergeContext.getWcInfo().getPath(), true),
                                                                     myFactory, sourceUrlUrl, getName(), false,
                                                                     myMergeContext.getBranchName());
    final TaskDescriptor taskDescriptor = TaskDescriptor.createForBackgroundableTask(task);
    // merge task will be the next after...
    context.next(taskDescriptor);
    // ... after we create changelist
    createChangelist(context);
  }

  private void createChangelist(final ContinuationPause context) {
    final ChangeListManager listManager = ChangeListManager.getInstance(myMergeContext.getProject());
    String name = myMergeContext.getTitle();
    int i = 1;
    boolean updateDefaultList = false;
    while (true) {
      final LocalChangeList changeList = listManager.findChangeList(name);
      if (changeList == null) {
        final LocalChangeList newList = listManager.addChangeList(name, null);
        listManager.setDefaultChangeList(newList);
        updateDefaultList = true;
        break;
      }
      if (changeList.getChanges().isEmpty()) {
        if (!changeList.isDefault()) {
          listManager.setDefaultChangeList(changeList);
          updateDefaultList = true;
        }
        break;
      }
      name = myMergeContext.getTitle() + " (" + i + ")";
      ++i;
    }
    if (updateDefaultList) {
      context.suspend();
      listManager.invokeAfterUpdate(new Runnable() {
        public void run() {
          context.ping();
        }
      }, InvokeAfterUpdateMode.BACKGROUND_NOT_CANCELLABLE_NOT_AWT, "", ModalityState.NON_MODAL);
    }
  }
}
