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

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.vcs.changes.ChangesUtil.getAfterRevisionsFiles;
import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static java.util.stream.Collectors.toList;

public class ShelveLocalChangesTask extends BaseMergeTask {

  @NotNull private final Intersection myIntersection;

  public ShelveLocalChangesTask(@NotNull QuickMerge mergeProcess, @NotNull Intersection intersection) {
    super(mergeProcess, "Shelving local changes before merge", Where.POOLED);

    myIntersection = intersection;
  }

  @Override
  public void run() {
    List<VirtualFile> changedFiles = shelveChanges();

    suspend();
    RefreshQueue.getInstance().refresh(true, false, this::ping, changedFiles);
  }

  @NotNull
  private List<VirtualFile> shelveChanges() {
    List<VirtualFile> changedFiles = newArrayList();
    ShelveChangesManager shelveManager = ShelveChangesManager.getInstance(myMergeContext.getProject());

    getApplication().invokeAndWait(() -> FileDocumentManager.getInstance().saveAllDocuments());

    for (Map.Entry<String, List<Change>> entry : myIntersection.getChangesByLists().entrySet()) {
      try {
        shelveManager
          .shelveChanges(entry.getValue(), myIntersection.getComment(entry.getKey()) + " (auto shelve before merge)", true, true);
        // TODO: ChangesUtil.getFilesFromChanges() performs refresh of several files.
        // TODO: Check if logic of collecting files to refresh could be revised here.
        changedFiles.addAll(getAfterRevisionsFiles(entry.getValue().stream(), true).collect(toList()));
      }
      catch (IOException e) {
        end(new VcsException(e));
      }
      catch (VcsException e) {
        end(e);
      }
    }

    return changedFiles;
  }
}
