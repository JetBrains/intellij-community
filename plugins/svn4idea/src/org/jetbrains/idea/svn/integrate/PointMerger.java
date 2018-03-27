// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.copy.CopyMoveClient;
import org.jetbrains.idea.svn.delete.DeleteClient;
import org.jetbrains.idea.svn.history.SvnRepositoryContentRevision;
import org.jetbrains.idea.svn.update.UpdateEventHandler;

import java.io.File;
import java.util.Comparator;
import java.util.List;

public class PointMerger extends Merger {

  @NotNull private final List<Change> mySelectedChanges;

  public PointMerger(final SvnVcs vcs,
                     CommittedChangeList selectedChangeList,
                     final File target,
                     final UpdateEventHandler handler,
                     final Url currentBranchUrl,
                     @NotNull List<Change> selectedChanges,
                     String branchName) {
    super(vcs, ContainerUtil.newArrayList(selectedChangeList), target, handler, currentBranchUrl, branchName);

    mySelectedChanges = ContainerUtil.sorted(selectedChanges, ChangesComparator.getInstance());
  }

  protected void doMerge() throws VcsException {
    for (Change change : mySelectedChanges) {
      SvnRepositoryContentRevision before = (SvnRepositoryContentRevision)change.getBeforeRevision();
      SvnRepositoryContentRevision after = (SvnRepositoryContentRevision)change.getAfterRevision();

      if (before == null) {
        //noinspection ConstantConditions
        add(after);
      }
      else if (after == null) {
        delete(before);
      }
      else {
        merge(before, after);
      }
    }
  }

  private void merge(@NotNull SvnRepositoryContentRevision before, @NotNull SvnRepositoryContentRevision after) throws VcsException {
    MergeClient client = myVcs.getFactory(myTarget).createMergeClient();
    Target source1 = before.toTarget();
    Target source2 = after.toTarget();
    File localPath = getLocalPath(after.getFullPath());

    client.merge(source1, source2, localPath, Depth.FILES, true, mySvnConfig.isMergeDryRun(), false, false, mySvnConfig.getMergeOptions(),
                 myHandler);
  }

  private void delete(@NotNull SvnRepositoryContentRevision revision) throws VcsException {
    DeleteClient client = myVcs.getFactory(myTarget).createDeleteClient();
    File localPath = getLocalPath(revision.getFullPath());

    client.delete(localPath, false, mySvnConfig.isMergeDryRun(), myHandler);
  }

  private void add(@NotNull SvnRepositoryContentRevision revision) throws VcsException {
    // todo dry run
    CopyMoveClient client = myVcs.getFactory(myTarget).createCopyMoveClient();
    File localPath = getLocalPath(revision.getFullPath());

    client.copy(revision.toTarget(), localPath, revision.getRevisionNumber().getRevision(), true, myHandler);
  }

  @NotNull
  private File getLocalPath(@NotNull String fullUrl) {
    return SvnUtil.fileFromUrl(myTarget, myCurrentBranchUrl.toString(), fullUrl);
  }

  private static class ChangesComparator implements Comparator<Change> {
    private final static ChangesComparator ourInstance = new ChangesComparator();

    public static ChangesComparator getInstance() {
      return ourInstance;
    }

    public int compare(final Change o1, final Change o2) {
      final SvnRepositoryContentRevision after1 = (SvnRepositoryContentRevision) o1.getAfterRevision();
      final SvnRepositoryContentRevision after2 = (SvnRepositoryContentRevision) o2.getAfterRevision();

      // "delete" changes to the end
      if (after1 == null) {
        return 1;
      }
      if (after2 == null) {
        return -1;
      }

      final String path1 = after1.getFullPath();
      final String path2 = after2.getFullPath();

      final String ancestor = Url.getCommonAncestor(path1, path2);
      return path1.equals(ancestor) ? -1 : 1;
    }
  }

  @Nullable
  @Override
  public File getMergeInfoHolder() {
    return null;
  }
}
