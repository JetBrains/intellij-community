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

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.util.ArrayList;
import java.util.List;

public class SvnLogUtil implements SvnLogLoader {
  private final Project myProject;
  private final SvnVcs myVcs;
  private final SvnRepositoryLocation myLocation;
  private final SVNURL myRepositoryRoot;

  public SvnLogUtil(final Project project, final SvnVcs vcs, final SvnRepositoryLocation location, final SVNURL repositoryRoot) {
    myProject = project;
    myVcs = vcs;
    myLocation = location;
    myRepositoryRoot = repositoryRoot;
  }

  public List<CommittedChangeList> loadInterval(final SVNRevision fromIncluding, final SVNRevision toIncluding,
                                                final int maxCount, final boolean includingYoungest, final boolean includeOldest)
    throws VcsException {
    final List<CommittedChangeList> result = new ArrayList<>();
    LogEntryConsumer handler = createLogHandler(fromIncluding, toIncluding, includingYoungest, includeOldest, result);
    SvnTarget target = SvnTarget.fromURL(myLocation.toSvnUrl());

    myVcs.getFactory(target).createHistoryClient().doLog(target, fromIncluding, toIncluding, true, true, false, maxCount, null, handler);

    return result;
  }

  @NotNull
  private LogEntryConsumer createLogHandler(final SVNRevision fromIncluding,
                                               final SVNRevision toIncluding,
                                               final boolean includingYoungest,
                                               final boolean includeOldest, final List<CommittedChangeList> result) {
    return new LogEntryConsumer() {
      @Override
      public void consume(LogEntry logEntry) {
        if (myProject.isDisposed()) throw new ProcessCanceledException();
        final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        if (progress != null) {
          progress.setText2(SvnBundle.message("progress.text2.processing.revision", logEntry.getRevision()));
          progress.checkCanceled();
        }
        if ((!includingYoungest) && (logEntry.getRevision() == fromIncluding.getNumber())) {
          return;
        }
        if ((!includeOldest) && (logEntry.getRevision() == toIncluding.getNumber())) {
          return;
        }
        result.add(new SvnChangeList(myVcs, myLocation, logEntry, myRepositoryRoot.toString()));
      }
    };
  }
}
