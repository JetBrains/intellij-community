// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.idea.svn.api.ErrorCode;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;

import java.util.Map;

import static com.google.common.net.UrlEscapers.urlFragmentEscaper;
import static org.jetbrains.idea.svn.SvnUtil.ensureStartSlash;
import static org.jetbrains.idea.svn.SvnUtil.getRelativeUrl;

public class LatestExistentSearcher {

  private static final Logger LOG = Logger.getInstance(LatestExistentSearcher.class);

  private long myStartNumber;
  private boolean myStartExistsKnown;
  @NotNull private final Url myUrl;
  @NotNull private final Url myRepositoryUrl;
  @NotNull private final String myRelativeUrl;
  private final SvnVcs myVcs;
  private long myEndNumber;

  public LatestExistentSearcher(final SvnVcs vcs, @NotNull Url url, @NotNull Url repositoryUrl) {
    this(0, -1, false, vcs, url, repositoryUrl);
  }

  public LatestExistentSearcher(final long startNumber,
                                final long endNumber,
                                final boolean startExistsKnown,
                                final SvnVcs vcs,
                                @NotNull Url url,
                                @NotNull Url repositoryUrl) {
    myStartNumber = startNumber;
    myEndNumber = endNumber;
    myStartExistsKnown = startExistsKnown;
    myVcs = vcs;
    myUrl = url;
    myRepositoryUrl = repositoryUrl;
    // TODO: Make utility method that compare relative urls checking all possible cases when start/end slash exists or not.
    myRelativeUrl = ensureStartSlash(urlFragmentEscaper().escape(getRelativeUrl(myRepositoryUrl, myUrl)));
  }

  public long getDeletionRevision() {
    if (! detectStartRevision()) return -1;

    final Ref<Long> latest = new Ref<>(-1L);
    try {
      if (myEndNumber == -1) {
        myEndNumber = getLatestRevision();
      }

      final Url existingParent = getExistingParent(myUrl);
      if (existingParent == null) {
        return myStartNumber;
      }

      final Revision startRevision = Revision.of(myStartNumber);
      Target target = Target.on(existingParent, startRevision);
      myVcs.getFactory(target).createHistoryClient().doLog(target, startRevision, Revision.HEAD, false, true, false, 0, null,
                                                           createHandler(latest));
    }
    catch (VcsException e) {
      LOG.info(e);
    }

    return latest.get().longValue();
  }

  @NotNull
  private LogEntryConsumer createHandler(@NotNull final Ref<Long> latest) {
    return logEntry -> {
      final Map changedPaths = logEntry.getChangedPaths();
      for (Object o : changedPaths.values()) {
        final LogEntryPath path = (LogEntryPath)o;
        if ((path.getType() == 'D') && (myRelativeUrl.equals(path.getPath()))) {
          latest.set(logEntry.getRevision());
          throw new SvnBindException("Latest existent revision found for " + myRelativeUrl);
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
      final RootUrlInfo rootUrlInfo = mapping.getWcRootForUrl(myUrl);
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
  private Url getExistingParent(Url url) throws SvnBindException {
    while (url != null && !url.equals(myRepositoryUrl) && !existsInRevision(url, myEndNumber)) {
      url = SvnUtil.removePathTail(url);
    }

    return url;
  }

  private boolean existsInRevision(@NotNull Url url, long revisionNumber) throws SvnBindException {
    Revision revision = Revision.of(revisionNumber);
    Info info = null;

    try {
      info = myVcs.getInfo(url, revision, revision);
    }
    catch (SvnBindException e) {
      // throw error if not "does not exist" error code
      if (!e.contains(ErrorCode.RA_ILLEGAL_URL)) {
        throw e;
      }
    }

    return info != null;
  }

  private long getLatestRevision() throws SvnBindException {
    return SvnUtil.getHeadRevision(myVcs, myRepositoryUrl).getNumber();
  }
}
