// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.ErrorCode;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.util.Iterator;
import java.util.List;

public class LiveProvider implements BunchProvider {
  private final SvnLogLoader myLoader;
  private final SvnRepositoryLocation myLocation;
  private final Url myRepositoryUrl;
  private boolean myEarliestRevisionWasAccessed;
  private final long myYoungestRevision;
  private final SvnVcs myVcs;

  public LiveProvider(final SvnVcs vcs,
                      final SvnRepositoryLocation location,
                      final long latestRevision,
                      final SvnLogLoader loader,
                      Url repositoryUrl) {
    myVcs = vcs;
    myLoader = loader;
    myLocation = location;
    myYoungestRevision = latestRevision;
    myRepositoryUrl = repositoryUrl;
  }

  public long getEarliestRevision() {
    return -1;
  }

  public boolean isEmpty() {
    return false;
  }

  public Fragment getEarliestBunchInInterval(final long earliestRevision, final long oldestRevision, final int desirableSize,
                                             final boolean includeYoungest, final boolean includeOldest) throws VcsException {
    return getEarliestBunchInIntervalImpl(earliestRevision, oldestRevision, desirableSize, includeYoungest, includeOldest, earliestRevision);
  }

  private Fragment getEarliestBunchInIntervalImpl(long earliestRevision,
                                                  final long oldestRevision,
                                                  final int desirableSize,
                                                  final boolean includeYoungest,
                                                  final boolean includeOldest, final long earliestToTake) throws VcsException {
    if ((myEarliestRevisionWasAccessed) || ((oldestRevision == myYoungestRevision) && ((! includeYoungest) || (! includeOldest)))) {
      return null;
    }
    final Revision youngRevision = (earliestRevision == -1) ? Revision.HEAD : Revision.of(earliestRevision);

    final Ref<List<CommittedChangeList>> refToList = new Ref<>();
    final Ref<VcsException> exceptionRef = new Ref<>();

    final Runnable loader = () -> {
      try {
        refToList
          .set(myLoader.loadInterval(youngRevision, Revision.of(oldestRevision), desirableSize, includeYoungest, includeOldest));
      }
      catch (VcsException e) {
        exceptionRef.set(e);
      }
    };

    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() || ! application.isDispatchThread()) {
      loader.run();
    } else {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        final ProgressIndicator ind = ProgressManager.getInstance().getProgressIndicator();
        if (ind != null) {
          ind.setText(SvnBundle.message("progress.live.provider.loading.revisions.details.text"));
        }
        loader.run();
      }, SvnBundle.message("progress.live.provider.loading.revisions.text"), false, myVcs.getProject());
    }

    if (!exceptionRef.isNull()) {
      final VcsException e = exceptionRef.get();
      if (isElementNotFound(e)) {
        // occurs when target URL is deleted in repository
        // try to find latest existent revision. expensive ...
        final LatestExistentSearcher searcher = new LatestExistentSearcher(oldestRevision, myYoungestRevision, (oldestRevision != 0),
                                                                           myVcs, myLocation.toSvnUrl(), myRepositoryUrl);
        final long existent = searcher.getLatestExistent();
        if ((existent == -1) || (existent == earliestRevision)) {
          myEarliestRevisionWasAccessed = true;
          return null;
        }
        return getEarliestBunchInIntervalImpl(existent, oldestRevision, includeYoungest ? desirableSize : (desirableSize + 1), true,
                                              includeOldest,
                                              Math.min(existent, earliestRevision));
      }
      throw e;
    }

    final List<CommittedChangeList> list = refToList.get();
    if (list.isEmpty()) {
      myEarliestRevisionWasAccessed = (oldestRevision == 0);
      return null;
    }
    if (earliestToTake > 0) {
      for (Iterator<CommittedChangeList> iterator = list.iterator(); iterator.hasNext(); ) {
        final CommittedChangeList changeList = iterator.next();
        if (changeList.getNumber() > earliestToTake) iterator.remove();
      }
    }
    myEarliestRevisionWasAccessed = (oldestRevision == 0) && ((list.size() + ((! includeOldest) ? 1 : 0) + ((! includeYoungest) ? 1 : 0)) < desirableSize);
    return new Fragment(Origin.LIVE, list, true, true, null);
  }

  private static boolean isElementNotFound(@NotNull VcsException e) {
    return e instanceof SvnBindException && ((SvnBindException)e).contains(ErrorCode.FS_NOT_FOUND);
  }

  public boolean isEarliestRevisionWasAccessed() {
    return myEarliestRevisionWasAccessed;
  }
}
