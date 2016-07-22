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
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.util.Iterator;
import java.util.List;

public class LiveProvider implements BunchProvider {
  private final SvnLogLoader myLoader;
  private final SvnRepositoryLocation myLocation;
  private final SVNURL myRepositoryUrl;
  private boolean myEarliestRevisionWasAccessed;
  private final long myYoungestRevision;
  private final SvnVcs myVcs;

  public LiveProvider(final SvnVcs vcs,
                      final SvnRepositoryLocation location,
                      final long latestRevision,
                      final SvnLogLoader loader,
                      SVNURL repositoryUrl) {
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
    final SVNRevision youngRevision = (earliestRevision == -1) ? SVNRevision.HEAD : SVNRevision.create(earliestRevision);

    final Ref<List<CommittedChangeList>> refToList = new Ref<>();
    final Ref<VcsException> exceptionRef = new Ref<>();

    final Runnable loader = new Runnable() {
      public void run() {
        try {
          refToList.set(
              myLoader.loadInterval(youngRevision, SVNRevision.create(oldestRevision), desirableSize, includeYoungest, includeOldest));
        }
        catch (VcsException e) {
          exceptionRef.set(e);
        }
      }
    };

    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() || ! application.isDispatchThread()) {
      loader.run();
    } else {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          final ProgressIndicator ind = ProgressManager.getInstance().getProgressIndicator();
          if (ind != null) {
            ind.setText(SvnBundle.message("progress.live.provider.loading.revisions.details.text"));
          }
          loader.run();
        }
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
    return e instanceof SvnBindException && ((SvnBindException)e).contains(SVNErrorCode.FS_NOT_FOUND);
  }

  public boolean isEarliestRevisionWasAccessed() {
    return myEarliestRevisionWasAccessed;
  }
}
