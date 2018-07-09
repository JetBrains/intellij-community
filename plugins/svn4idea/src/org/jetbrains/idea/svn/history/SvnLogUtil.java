// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;

import java.util.ArrayList;
import java.util.List;

public class SvnLogUtil implements SvnLogLoader {
  private final Project myProject;
  private final SvnVcs myVcs;
  private final SvnRepositoryLocation myLocation;
  private final Url myRepositoryRoot;

  public SvnLogUtil(final Project project, final SvnVcs vcs, final SvnRepositoryLocation location, final Url repositoryRoot) {
    myProject = project;
    myVcs = vcs;
    myLocation = location;
    myRepositoryRoot = repositoryRoot;
  }

  public List<CommittedChangeList> loadInterval(final Revision fromIncluding, final Revision toIncluding,
                                                final int maxCount, final boolean includingYoungest, final boolean includeOldest)
    throws VcsException {
    final List<CommittedChangeList> result = new ArrayList<>();
    LogEntryConsumer handler = createLogHandler(fromIncluding, toIncluding, includingYoungest, includeOldest, result);
    Target target = Target.on(myLocation.toSvnUrl());

    myVcs.getFactory(target).createHistoryClient().doLog(target, fromIncluding, toIncluding, true, true, false, maxCount, null, handler);

    return result;
  }

  @NotNull
  private LogEntryConsumer createLogHandler(final Revision fromIncluding,
                                               final Revision toIncluding,
                                               final boolean includingYoungest,
                                               final boolean includeOldest, final List<CommittedChangeList> result) {
    return logEntry -> {
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
      result.add(new SvnChangeList(myVcs, myLocation, logEntry, myRepositoryRoot));
    };
  }
}
