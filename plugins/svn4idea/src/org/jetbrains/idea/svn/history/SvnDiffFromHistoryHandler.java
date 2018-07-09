/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.BaseDiffFromHistoryHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.ClientFactory;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.diff.DirectoryWithBranchComparer;

import java.io.File;
import java.util.List;

public class SvnDiffFromHistoryHandler extends BaseDiffFromHistoryHandler<SvnFileRevision> {

  @NotNull private final SvnVcs myVcs;

  public SvnDiffFromHistoryHandler(@NotNull SvnVcs vcs) {
    super(vcs.getProject());
    myVcs = vcs;
  }

  @NotNull
  @Override
  protected List<Change> getChangesBetweenRevisions(@NotNull FilePath path, @NotNull SvnFileRevision rev1, @Nullable SvnFileRevision rev2)
    throws VcsException {
    File file = path.getIOFile();
    Target target1 = Target.on(rev1.getURL(), rev1.getRevision());
    Target target2 = rev2 != null ? Target.on(rev2.getURL(), rev2.getRevision()) : Target.on(file);

    return executeDiff(path, target1, target2);
  }

  @NotNull
  @Override
  protected List<Change> getAffectedChanges(@NotNull FilePath path, @NotNull SvnFileRevision rev) throws VcsException {
    // Diff with zero revision is used here to get just affected changes under the path, and not all affected changes of the revision.
    Target target1 = Target.on(rev.getURL(), Revision.of(0));
    Target target2 = Target.on(rev.getURL(), rev.getRevision());

    return executeDiff(path, target1, target2);
  }

  @NotNull
  @Override
  protected String getPresentableName(@NotNull SvnFileRevision revision) {
    return revision.getRevisionNumber().asString();
  }

  @NotNull
  private List<Change> executeDiff(@NotNull FilePath path, @NotNull Target target1, @NotNull Target target2) throws VcsException {
    File file = path.getIOFile();
    ClientFactory factory = target2.isUrl() ? myVcs.getFactory(file) : DirectoryWithBranchComparer.getClientFactory(myVcs, file);

    return factory.createDiffClient().compare(target1, target2);
  }
}
