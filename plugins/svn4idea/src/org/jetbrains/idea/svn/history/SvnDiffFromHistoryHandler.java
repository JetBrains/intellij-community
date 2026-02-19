// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  private final @NotNull SvnVcs myVcs;

  public SvnDiffFromHistoryHandler(@NotNull SvnVcs vcs) {
    super(vcs.getProject());
    myVcs = vcs;
  }

  @Override
  protected @NotNull List<Change> getChangesBetweenRevisions(@NotNull FilePath path, @NotNull SvnFileRevision rev1, @Nullable SvnFileRevision rev2)
    throws VcsException {
    File file = path.getIOFile();
    Target target1 = Target.on(rev1.getURL(), rev1.getRevision());
    Target target2 = rev2 != null ? Target.on(rev2.getURL(), rev2.getRevision()) : Target.on(file);

    return executeDiff(path, target1, target2);
  }

  @Override
  protected @NotNull List<Change> getAffectedChanges(@NotNull FilePath path, @NotNull SvnFileRevision rev) throws VcsException {
    // Diff with zero revision is used here to get just affected changes under the path, and not all affected changes of the revision.
    Target target1 = Target.on(rev.getURL(), Revision.of(0));
    Target target2 = Target.on(rev.getURL(), rev.getRevision());

    return executeDiff(path, target1, target2);
  }

  @Override
  protected @NotNull String getPresentableName(@NotNull SvnFileRevision revision) {
    return revision.getRevisionNumber().asString();
  }

  private @NotNull List<Change> executeDiff(@NotNull FilePath path, @NotNull Target target1, @NotNull Target target2) throws VcsException {
    File file = path.getIOFile();
    ClientFactory factory = target2.isUrl() ? myVcs.getFactory(file) : DirectoryWithBranchComparer.getClientFactory(myVcs, file);

    return factory.createDiffClient().compare(target1, target2);
  }
}
