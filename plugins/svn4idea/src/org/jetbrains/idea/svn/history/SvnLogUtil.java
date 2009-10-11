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
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.util.ArrayList;
import java.util.List;

public class SvnLogUtil implements SvnLogLoader {
  private final Project myProject;
  private final SvnVcs myVcs;
  private final SvnRepositoryLocation myLocation;
  private final SVNURL myRepositoryRoot;
  private final String myRelative;

  public SvnLogUtil(final Project project, final SvnVcs vcs, final SvnRepositoryLocation location, final SVNURL repositoryRoot) {
    myProject = project;
    myVcs = vcs;
    myLocation = location;
    myRepositoryRoot = repositoryRoot;

    final String repositoryRootPath = repositoryRoot.toString();
    myRelative = myLocation.getURL().substring(repositoryRootPath.length());
  }

  public List<CommittedChangeList> loadInterval(final SVNRevision fromIncluding, final SVNRevision toIncluding,
                                                final int maxCount, final boolean includingYoungest, final boolean includeOldest) throws SVNException {
    final List<CommittedChangeList> result = new ArrayList<CommittedChangeList>();
    loadRevisions(fromIncluding, toIncluding, null, maxCount, result, includingYoungest, includeOldest);
    return result;
  }

  private void loadRevisions(final SVNRevision fromIncluding, final SVNRevision toIncluding, final String author, final int maxCount,
                             final List<CommittedChangeList> result,
                             final boolean includingYoungest, final boolean includeOldest) throws SVNException {
    SVNLogClient logger = myVcs.createLogClient();
    logger.doLog(myRepositoryRoot, new String[]{myRelative}, SVNRevision.UNDEFINED, fromIncluding, toIncluding, true, true, maxCount,
                 new ISVNLogEntryHandler() {
                   public void handleLogEntry(SVNLogEntry logEntry) {
                     if (myProject.isDisposed()) throw new ProcessCanceledException();
                     final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
                     if (progress != null) {
                       progress.setText2(SvnBundle.message("progress.text2.processing.revision", logEntry.getRevision()));
                       progress.checkCanceled();
                     }
                     if ((! includingYoungest) && (logEntry.getRevision() == fromIncluding.getNumber())) {
                       return;
                     }
                     if ((! includeOldest) && (logEntry.getRevision() == toIncluding.getNumber())) {
                       return;
                     }
                     if (author == null || author.equalsIgnoreCase(logEntry.getAuthor())) {
                       result.add(new SvnChangeList(myVcs, myLocation, logEntry, myRepositoryRoot.toString()));
                     }
                   }
                 });
  }
}
