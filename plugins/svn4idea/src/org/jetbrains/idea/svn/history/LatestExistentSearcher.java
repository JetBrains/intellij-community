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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnFileUrlMapping;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.util.Map;

public class LatestExistentSearcher {

  private static final Logger LOG = Logger.getInstance(LatestExistentSearcher.class);

  private long myStartNumber;
  private boolean myStartExistsKnown;
  @NotNull private final SVNURL myUrl;
  @NotNull private final SVNURL myRepositoryUrl;
  @NotNull private final String myRelativeUrl;
  private final SvnVcs myVcs;
  private long myEndNumber;

  public LatestExistentSearcher(final SvnVcs vcs, @NotNull SVNURL url, @NotNull SVNURL repositoryUrl) {
    this(0, -1, false, vcs, url, repositoryUrl);
  }

  public LatestExistentSearcher(final long startNumber,
                                final long endNumber,
                                final boolean startExistsKnown,
                                final SvnVcs vcs,
                                @NotNull SVNURL url,
                                @NotNull SVNURL repositoryUrl) {
    myStartNumber = startNumber;
    myEndNumber = endNumber;
    myStartExistsKnown = startExistsKnown;
    myVcs = vcs;
    myUrl = url;
    myRepositoryUrl = repositoryUrl;
    // TODO: Make utility method that compare relative urls checking all possible cases when start/end slash exists or not.
    myRelativeUrl = SvnUtil.ensureStartSlash(SVNURLUtil.getRelativeURL(myRepositoryUrl, myUrl, true));
  }

  public long getDeletionRevision() {
    if (! detectStartRevision()) return -1;

    final Ref<Long> latest = new Ref<>(myStartNumber);
    try {
      if (myEndNumber == -1) {
        myEndNumber = getLatestRevision();
      }

      final SVNURL existingParent = getExistingParent(myUrl);
      if (existingParent == null) {
        return myStartNumber;
      }

      final SVNRevision startRevision = SVNRevision.create(myStartNumber);
      SvnTarget target = SvnTarget.fromURL(existingParent, startRevision);
      myVcs.getFactory(target).createHistoryClient().doLog(target, startRevision, SVNRevision.HEAD, false, true, false, 0, null,
                                                           createHandler(latest));
    }
    catch (VcsException e) {
      LOG.info(e);
    }

    return latest.get().longValue();
  }

  @NotNull
  private LogEntryConsumer createHandler(@NotNull final Ref<Long> latest) {
    return new LogEntryConsumer() {
      @Override
      public void consume(final LogEntry logEntry) throws SVNException {
        final Map changedPaths = logEntry.getChangedPaths();
        for (Object o : changedPaths.values()) {
          final LogEntryPath path = (LogEntryPath)o;
          if ((path.getType() == 'D') && (myRelativeUrl.equals(path.getPath()))) {
            latest.set(logEntry.getRevision());
            throw new SVNException(SVNErrorMessage.UNKNOWN_ERROR_MESSAGE);
          }
        }
      }
    };
  }

  public long getLatestExistent() {
    if (! detectStartRevision()) return myStartNumber;

    long latestOk = myStartNumber;
    try {
      if (myEndNumber == -1) {
        myEndNumber = getLatestRevision();
      }
      // TODO: At least binary search could be applied here for optimization
      for (long i = myStartNumber + 1; i < myEndNumber; i++) {
        if (existsInRevision(myUrl, i)) {
          latestOk = i;
        }
      }
    }
    catch (SvnBindException e) {
      LOG.info(e);
    }

    return latestOk;
  }

  private boolean detectStartRevision() {
    if (! myStartExistsKnown) {
      final SvnFileUrlMapping mapping = myVcs.getSvnFileUrlMapping();
      final RootUrlInfo rootUrlInfo = mapping.getWcRootForUrl(myUrl.toString());
      if (rootUrlInfo == null) return true;
      final VirtualFile vf = rootUrlInfo.getVirtualFile();
      final Info info = myVcs.getInfo(vf);
      if ((info == null) || (info.getRevision() == null)) {
        return false;
      }
      myStartNumber = info.getRevision().getNumber();
      myStartExistsKnown = true;
    }
    return true;
  }

  @Nullable
  private SVNURL getExistingParent(SVNURL url) throws SvnBindException {
    while (url != null && !url.equals(myRepositoryUrl) && !existsInRevision(url, myEndNumber)) {
      url = SvnUtil.removePathTail(url);
    }

    return url;
  }

  private boolean existsInRevision(@NotNull SVNURL url, long revisionNumber) throws SvnBindException {
    SVNRevision revision = SVNRevision.create(revisionNumber);
    Info info = null;

    try {
      info = myVcs.getInfo(url, revision, revision);
    }
    catch (SvnBindException e) {
      // throw error if not "does not exist" error code
      if (!e.contains(SVNErrorCode.RA_ILLEGAL_URL)) {
        throw e;
      }
    }

    return info != null;
  }

  private long getLatestRevision() throws SvnBindException {
    return SvnUtil.getHeadRevision(myVcs, myRepositoryUrl).getNumber();
  }
}
