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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.*;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

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
  private RootUrlInfo rootUrlInfo;
  private SvnChangeList[] changeList;
  private SVNRevision revisionBefore;
  private SVNURL repositoryUrl;
  private SVNURL svnRootUrl;
  private SvnRepositoryLocation svnRootLocation;
  private String repositoryRelativeUrl;
  private FilePath filePath;
  private SVNLogClient logger;

  SingleCommittedListProvider(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull VcsRevisionNumber number) {
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

    rootUrlInfo = myVcs.getSvnFileUrlMapping().getWcRootForFilePath(new File(file.getPath()));
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
      logger = myVcs.createLogClient();

      result = true;
    }

    return result;
  }

  private void calculate() throws VcsException {
    if (!tryExactHit(svnRootUrl) && !tryByRoot(repositoryUrl)) {
      filePath = getOneListStepByStep(svnRootUrl);
    }
    else {
      Change change = ContainerUtil.getFirstItem(changeList[0].getChanges());
      if (change != null) {
        final ContentRevision afterRevision = change.getAfterRevision();

        filePath = afterRevision != null ? afterRevision.getFile() : filePath;
      }
      else {
        final Change targetChange = changeList[0].getByPath(repositoryRelativeUrl);

        filePath = targetChange == null ? getOneListStepByStep(svnRootUrl) : filePath;
      }
    }
  }

  private FilePath getOneListStepByStep(SVNURL svnurl) throws VcsException {
    FilePath path = tryStepByStep(svnurl);

    return path == null ? filePath : path;
  }

  private boolean tryByRoot(SVNURL repositoryUrl) throws VcsException {
    final boolean authorized = SvnAuthenticationNotifier.passiveValidation(myProject, repositoryUrl);

    return authorized && tryExactHit(repositoryUrl);
  }

  // return changed path, if any
  private FilePath tryStepByStep(SVNURL svnurl) throws VcsException {
    try {
      final SvnCopyPathTracker pathTracker = new SvnCopyPathTracker(repositoryUrl.toDecodedString(), repositoryRelativeUrl);
      // TODO: Implement this with command line
      logger.doLog(svnurl, null, SVNRevision.UNDEFINED, SVNRevision.HEAD, revisionBefore,
                   false, true, false, 0, null,
                   new ISVNLogEntryHandler() {
                     public void handleLogEntry(SVNLogEntry logEntry) {
                       if (myProject.isDisposed()) throw new ProcessCanceledException();
                       if (logEntry.getDate() == null) {
                         // do not add lists without info - this situation is possible for lists where there are paths that user has no rights to observe
                         return;
                       }
                       pathTracker.accept(logEntry);
                       if (logEntry.getRevision() == revisionBefore.getNumber()) {
                         changeList[0] = new SvnChangeList(myVcs, svnRootLocation, logEntry, repositoryUrl.toDecodedString());
                       }
                     }
                   });
      return pathTracker.getFilePath(myVcs);
    }
    catch (SVNException e) {
      LOG.info(e);
      throw new VcsException(e);
    }
  }

  private boolean tryExactHit(SVNURL svnurl) throws VcsException {
    ISVNLogEntryHandler handler = new ISVNLogEntryHandler() {
      public void handleLogEntry(SVNLogEntry logEntry) {
        if (myProject.isDisposed()) throw new ProcessCanceledException();
        if (logEntry.getDate() == null) {
          // do not add lists without info - this situation is possible for lists where there are paths that user has no rights to observe
          return;
        }
        changeList[0] = new SvnChangeList(myVcs, svnRootLocation, logEntry, repositoryUrl.toString());
      }
    };
    try {
      // TODO: Implement this with command line
      logger.doLog(svnurl, null, SVNRevision.UNDEFINED, revisionBefore, revisionBefore, false, true, false, 1, null, handler);
    }
    catch (SVNException e) {
      LOG.info(e);
      if (SVNErrorCode.FS_CATEGORY != e.getErrorMessage().getErrorCode().getCategory()) {
        throw new VcsException(e);
      }
    }
    return changeList[0] != null;
  }
}
