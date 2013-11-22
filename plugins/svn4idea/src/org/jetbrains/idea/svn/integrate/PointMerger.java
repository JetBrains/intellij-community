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
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.copy.CopyMoveClient;
import org.jetbrains.idea.svn.delete.DeleteClient;
import org.jetbrains.idea.svn.history.SvnRepositoryContentRevision;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.*;

public class PointMerger extends Merger {
  private final List<Change> mySelectedChanges;
  private final SvnVcs myVcs;
  private final UpdateEventHandler myHandler;

  public PointMerger(final SvnVcs vcs,
                     CommittedChangeList selectedChangeList,
                     final File target,
                     final UpdateEventHandler handler,
                     final SVNURL currentBranchUrl,
                     final List<Change> selectedChanges,
                     String branchName) {
    super(vcs, new ArrayList<CommittedChangeList>(Arrays.<CommittedChangeList>asList(selectedChangeList)),
          target, handler, currentBranchUrl, branchName);
    myHandler = handler;
    myVcs = vcs;
    mySelectedChanges = selectedChanges;
    Collections.sort(mySelectedChanges, ChangesComparator.getInstance());
    myLatestProcessed = selectedChangeList;
  }

  public boolean hasNext() {
    return myCount == 0;
  }

  protected void doMerge() throws SVNException, VcsException {
    for (Change change : mySelectedChanges) {
      if (change.getBeforeRevision() == null) {
        add(change);
      } else if (change.getAfterRevision() == null) {
        delete(change);
      } else {
        merge(change);
      }
    }
  }

  private void merge(final Change change) throws SVNException, VcsException {
    final SvnRepositoryContentRevision before = (SvnRepositoryContentRevision) change.getBeforeRevision();
    final SvnRepositoryContentRevision after = (SvnRepositoryContentRevision) change.getAfterRevision();

    final String path = myCurrentBranchUrl.toString();
    final String beforeUrl = before.getFullPath();
    final String afterUrl = after.getFullPath();

    final File afterPath = SvnUtil.fileFromUrl(myTarget, path, afterUrl);

    MergeClient client = myVcs.getFactory(myTarget).createMergeClient();
    SvnTarget source1 = SvnTarget.fromURL(SVNURL.parseURIEncoded(beforeUrl), ((SvnRevisionNumber)before.getRevisionNumber()).getRevision());
    SvnTarget source2 = SvnTarget.fromURL(SVNURL.parseURIEncoded(afterUrl), ((SvnRevisionNumber) after.getRevisionNumber()).getRevision());

    client.merge(source1, source2, afterPath, SVNDepth.FILES, true, mySvnConfig.MERGE_DRY_RUN, false, false, mySvnConfig.getMergeOptions(),
                 myHandler);
  }

  private void delete(final Change change) throws SVNException, VcsException {
    final SvnRepositoryContentRevision before = (SvnRepositoryContentRevision) change.getBeforeRevision();
    final String path = myCurrentBranchUrl.toString();
    final String beforeUrl = before.getFullPath();
    final File beforePath = SvnUtil.fileFromUrl(myTarget, path, beforeUrl);

    DeleteClient client = myVcs.getFactory(myTarget).createDeleteClient();
    client.delete(beforePath, false, mySvnConfig.MERGE_DRY_RUN, myHandler);
  }

  private void add(final Change change) throws SVNException, VcsException {
    final SvnRepositoryContentRevision after = (SvnRepositoryContentRevision) change.getAfterRevision();
    final String path = myCurrentBranchUrl.toString();
    final String afterUrl = after.getFullPath();
    final File afterPath = SvnUtil.fileFromUrl(myTarget, path, afterUrl);

    final SVNRevision revision = ((SvnRevisionNumber)after.getRevisionNumber()).getRevision();
    // todo dry run
    CopyMoveClient client = myVcs.getFactory(myTarget).createCopyMoveClient();
    client.copy(SvnTarget.fromURL(SVNURL.parseURIEncoded(afterUrl), revision), afterPath, revision, true, myHandler);
  }

  private static class ChangesComparator implements Comparator<Change> {
    private final static ChangesComparator ourInstance = new ChangesComparator();

    public static ChangesComparator getInstance() {
      return ourInstance;
    }

    public int compare(final Change o1, final Change o2) {
      final SvnRepositoryContentRevision after1 = (SvnRepositoryContentRevision) o1.getAfterRevision();
      final SvnRepositoryContentRevision after2 = (SvnRepositoryContentRevision) o2.getAfterRevision();

      if (after1 == null) {
        return 1;
      }
      if (after2 == null) {
        return -1;
      }

      final String path1 = after1.getFullPath();
      final String path2 = after2.getFullPath();
      if (path1 == null) {
        return 1;
      }
      if (path2 == null) {
        return -1;
      }

      final String ancestor = SVNPathUtil.getCommonPathAncestor(path1, path2);
      return (path1.equals(ancestor)) ? -1 : 1;
    }
  }

  @Nullable
  @Override
  public File getMergeInfoHolder() {
    return null;
  }
}
