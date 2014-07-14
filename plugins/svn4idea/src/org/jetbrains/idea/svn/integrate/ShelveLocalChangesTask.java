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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vfs.newvfs.RefreshSessionImpl;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.MergeContext;

import java.io.IOException;
import java.util.Collection;

/**
 * @author Konstantin Kolosovsky.
 */
public class ShelveLocalChangesTask extends BaseMergeTask {
  private final Intersection myIntersection;

  public ShelveLocalChangesTask(@NotNull MergeContext mergeContext,
                                @NotNull QuickMergeInteraction interaction,
                                final Intersection intersection) {
    super(mergeContext, interaction, "Shelving local changes before merge", Where.POOLED);
    myIntersection = intersection;
  }

  @Override
  public void run(final ContinuationContext context) {
    final MultiMap<String, Change> map = myIntersection.getChangesSubset();

    final RefreshSessionImpl session = new RefreshSessionImpl(true, false, new Runnable() {
      public void run() {
        context.ping();
      }
    });

    for (String name : map.keySet()) {
      try {
        final Collection<Change> changes = map.get(name);
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          @Override
          public void run() {
            FileDocumentManager.getInstance().saveAllDocuments();
          }
        }, ModalityState.NON_MODAL);
        ShelveChangesManager.getInstance(myMergeContext.getProject()).shelveChanges(changes, myIntersection.getComment(name) +
                                                                                             " (auto shelve before merge)",
                                                                                    true
        );
        session.addAllFiles(ChangesUtil.getFilesFromChanges(changes));
      }
      catch (IOException e) {
        finishWithError(context, e.getMessage(), true);
      }
      catch (VcsException e) {
        finishWithError(context, e.getMessage(), true);
      }
    }
    // first suspend to guarantee stop->then start back sequence
    context.suspend();
    session.launch();
  }
}
