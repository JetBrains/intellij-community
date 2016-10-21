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
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.application.ApplicationManager.getApplication;

public class ShelveLocalChangesTask extends BaseMergeTask {

  @NotNull private final Intersection myIntersection;

  public ShelveLocalChangesTask(@NotNull QuickMerge mergeProcess, @NotNull Intersection intersection) {
    super(mergeProcess, "Shelving local changes before merge", Where.POOLED);

    myIntersection = intersection;
  }

  @Override
  public void run() throws VcsException {
    try {
      shelveChanges();
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  private void shelveChanges() throws VcsException, IOException {
    getApplication().invokeAndWait(() -> FileDocumentManager.getInstance().saveAllDocuments());

    ShelveChangesManager shelveManager = ShelveChangesManager.getInstance(myMergeContext.getProject());

    for (Map.Entry<String, List<Change>> entry : myIntersection.getChangesByLists().entrySet()) {
      String shelfName = myIntersection.getComment(entry.getKey()) + " (auto shelve before merge)";

      shelveManager.shelveChanges(entry.getValue(), shelfName, true, true);
    }
  }
}
