/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.auth.SvnAuthenticationNotifier;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
* @author Konstantin Kolosovsky.
*/
public class SingleCommittedListProvider {

  private static final Logger LOG = Logger.getInstance(SingleCommittedListProvider.class);

  @NotNull private final SvnVcs myVcs;
  @NotNull private final Project myProject;
  @NotNull private final VirtualFile file;
  @NotNull private final VcsRevisionNumber number;
  private SvnChangeList[] changeList;
  private SVNRevision revisionBefore;
  private SVNURL repositoryUrl;
  private SVNURL svnRootUrl;
  private SvnRepositoryLocation svnRootLocation;
  private String repositoryRelativeUrl;
  private FilePath filePath;

  public SingleCommittedListProvider(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull VcsRevisionNumber number) {
    myVcs = vcs;
    myProject = vcs.getProject();
    this.file = file;
    this.number = number;
  }

  public Pair<SvnChangeList, FilePath> run() throws VcsException {
    Pair<SvnChangeList, FilePath> result = null;

    if (setup()) {
      calculate();
      result = Pair.create(changeList[0], filePath);
    }

    return result;
  }

  private boolean setup() {
    boolean result = false;

    RootUrlInfo rootUrlInfo = myVcs.getSvnFileUrlMapping().getWcRootForFilePath(new File(file.getPath()));
    if (rootUrlInfo != null) {
      changeList = new SvnChangeList[1];
      revisionBefore = ((SvnRevisionNumber)number).getRevision();
      repositoryUrl = rootUrlInfo.getRepositoryUrlUrl();
      svnRootUrl = rootUrlInfo.getAbsoluteUrlAsUrl();
      svnRootLocation = new SvnRepositoryLocation(rootUrlInfo.getAbsoluteUrl());
      repositoryRelativeUrl = SvnUtil.ensureStartSlash(SvnUtil.join(
        SvnUtil.getRelativeUrl(repositoryUrl.toDecodedString(), svnRootUrl.toDecodedString()),
        SvnUtil.getRelativePath(rootUrlInfo.getPath(), file.getPath())));

      filePath = VcsUtil.getFilePath(file);

      result = true;
    }

    return result;
  }

  private void calculate() throws VcsException {
    // TODO: Seems that filePath detection could be replaced with "svn info -r <revisionBefore>" - and not call
    // TODO: "svn log -r HEAD:<revisionBefore>" and track copies manually (which also is not correct for all cases).
    if (!searchForUrl(svnRootUrl) && !(hasAccess(repositoryUrl) && searchForUrl(repositoryUrl))) {
      filePath = searchFromHead(svnRootUrl);
    }
    else {
      if (changeList[0].getChanges().size() == 1) {
        final ContentRevision afterRevision = changeList[0].getChanges().iterator().next().getAfterRevision();

        filePath = afterRevision != null ? afterRevision.getFile() : filePath;
      }
      else {
        final Change targetChange = changeList[0].getByPath(repositoryRelativeUrl);

        filePath = targetChange == null ? searchFromHead(svnRootUrl) : filePath;
      }
    }
  }

  private boolean hasAccess(@NotNull SVNURL url) {
    return SvnAuthenticationNotifier.passiveValidation(myProject, url);
  }

  // return changed path, if any
  private FilePath searchFromHead(@NotNull SVNURL url) throws VcsException {
    final SvnCopyPathTracker pathTracker = new SvnCopyPathTracker(repositoryUrl.toDecodedString(), repositoryRelativeUrl);
    SvnTarget target = SvnTarget.fromURL(url);

    myVcs.getFactory(target).createHistoryClient().doLog(target, SVNRevision.HEAD, revisionBefore, false, true, false, 0, null,
        new LogEntryConsumer() {
          @Override
          public void consume(LogEntry logEntry) {
            checkDisposed();
            // date could be null for lists where there are paths that user has no rights to observe
            if (logEntry.getDate() != null) {
              pathTracker.accept(logEntry);
              if (logEntry.getRevision() == revisionBefore.getNumber()) {
                changeList[0] = createChangeList(logEntry);
              }
            }
          }
        }
    );

    FilePath path = pathTracker.getFilePath(myVcs);
    return path == null ? filePath : path;
  }

  @NotNull
  private SvnChangeList createChangeList(@NotNull LogEntry logEntry) {
    return new SvnChangeList(myVcs, svnRootLocation, logEntry, repositoryUrl.toDecodedString());
  }

  private void checkDisposed() {
    if (myProject.isDisposed()) {
      throw new ProcessCanceledException();
    }
  }

  private boolean searchForUrl(@NotNull SVNURL url) throws VcsException {
    LogEntryConsumer handler = new LogEntryConsumer() {
      @Override
      public void consume(LogEntry logEntry) {
        checkDisposed();
        // date could be null for lists where there are paths that user has no rights to observe
        if (logEntry.getDate() != null) {
          changeList[0] = createChangeList(logEntry);
        }
      }
    };

    SvnTarget target = SvnTarget.fromURL(url);
    try {
      myVcs.getFactory(target).createHistoryClient().doLog(target, revisionBefore, revisionBefore, false, true, false, 1, null, handler);
    }
    catch (SvnBindException e) {
      LOG.info(e);
      if (!e.containsCategory(SVNErrorCode.FS_CATEGORY)) {
        throw e;
      }
    }
    return changeList[0] != null;
  }
}
