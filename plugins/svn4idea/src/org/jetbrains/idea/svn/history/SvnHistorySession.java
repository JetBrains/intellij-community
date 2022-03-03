// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.info.Info;

import java.io.File;
import java.util.List;

public class SvnHistorySession extends VcsAbstractHistorySession {
  private final SvnVcs myVcs;
  private final FilePath myCommittedPath;
  private final boolean myHaveMergeSources;
  private final boolean myHasLocalSource;

  public SvnHistorySession(SvnVcs vcs, final List<? extends VcsFileRevision> revisions, final FilePath committedPath, final boolean haveMergeSources,
                           @Nullable final VcsRevisionNumber currentRevision, boolean skipRefreshOnStart, boolean source) {
    super(revisions, currentRevision);
    myVcs = vcs;
    myCommittedPath = committedPath;
    myHaveMergeSources = haveMergeSources;
    myHasLocalSource = source;
    if (!skipRefreshOnStart) {
      shouldBeRefreshed();
    }
  }

  @Override
  @Nullable
  public VcsRevisionNumber calcCurrentRevisionNumber() {
    if (myCommittedPath == null) {
      return null;
    }
    if (myCommittedPath.isNonLocal()) {
      // technically, it does not make sense, since there's no "current" revision for non-local history (if look how it's used)
      // but ok, lets keep it for now
      return new SvnRevisionNumber(Revision.HEAD);
    }
    return getCurrentCommittedRevision(myVcs, new File(myCommittedPath.getPath()));
  }

  public static VcsRevisionNumber getCurrentCommittedRevision(final SvnVcs vcs, final File file) {
    Info info = vcs.getInfo(file);
    return info != null ? new SvnRevisionNumber(info.getCommitInfo().getRevision()) : null;
  }

  public FilePath getCommittedPath() {
    return myCommittedPath;
  }

  public boolean isHaveMergeSources() {
    return myHaveMergeSources;
  }

  @Override
  public VcsHistorySession copy() {
    return new SvnHistorySession(myVcs, getRevisionList(), myCommittedPath, myHaveMergeSources, getCurrentRevisionNumber(), true,
                                 myHasLocalSource);
  }

  @Override
  public boolean hasLocalSource() {
    return myHasLocalSource;
  }
}
