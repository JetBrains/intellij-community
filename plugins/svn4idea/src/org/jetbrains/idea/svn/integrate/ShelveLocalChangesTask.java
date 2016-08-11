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
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.vcs.changes.ChangesUtil.getAfterRevisionsFiles;
import static java.util.stream.Collectors.toList;

/**
 * @author Konstantin Kolosovsky.
 */
public class ShelveLocalChangesTask extends BaseMergeTask {

  @NotNull private final Intersection myIntersection;

  public ShelveLocalChangesTask(@NotNull MergeContext mergeContext,
                                @NotNull QuickMergeInteraction interaction,
                                @NotNull Intersection intersection) {
    super(mergeContext, interaction, "Shelving local changes before merge", Where.POOLED);

    myIntersection = intersection;
  }

  @Override
  public void run(final ContinuationContext context) {
    List<VirtualFile> changedFiles = shelveChanges(context);

    context.suspend();
    RefreshQueue.getInstance().refresh(true, false, new Runnable() {
      @Override
      public void run() {
        context.ping();
      }
    }, changedFiles);
  }

  @NotNull
  private List<VirtualFile> shelveChanges(@NotNull ContinuationContext context) {
    List<VirtualFile> changedFiles = ContainerUtil.newArrayList();
    ShelveChangesManager shelveManager = ShelveChangesManager.getInstance(myMergeContext.getProject());

    for (Map.Entry<String, Collection<Change>> entry : myIntersection.getChangesSubset().entrySet()) {
      try {
        // TODO: Could this be done once before for loop?
        saveAllDocuments();

        shelveManager.shelveChanges(entry.getValue(), myIntersection.getComment(entry.getKey()) + " (auto shelve before merge)", true);
        // TODO: ChangesUtil.getFilesFromChanges() performs refresh of several files.
        // TODO: Check if logic of collecting files to refresh could be revised here.
        changedFiles.addAll(getAfterRevisionsFiles(entry.getValue().stream(), true).collect(toList()));
      }
      catch (IOException e) {
        finishWithError(context, e.getMessage(), true);
      }
      catch (VcsException e) {
        finishWithError(context, e.getMessage(), true);
      }
    }

    return changedFiles;
  }

  private static void saveAllDocuments() {
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    }, ModalityState.NON_MODAL);
  }
}
