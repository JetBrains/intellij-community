// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.ErrorCategory;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.auth.SvnAuthenticationNotifier;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class SingleCommittedListProvider {

  private static final Logger LOG = Logger.getInstance(SingleCommittedListProvider.class);

  @NotNull private final SvnVcs myVcs;
  @NotNull private final Project myProject;
  @NotNull private final VirtualFile file;
  @NotNull private final VcsRevisionNumber number;
  private SvnChangeList[] changeList;
  private Revision revisionBefore;
  private Url repositoryUrl;
  private Url svnRootUrl;
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

    RootUrlInfo rootUrlInfo = myVcs.getSvnFileUrlMapping().getWcRootForFilePath(virtualToIoFile(file));
    if (rootUrlInfo != null) {
      changeList = new SvnChangeList[1];
      revisionBefore = ((SvnRevisionNumber)number).getRevision();
      repositoryUrl = rootUrlInfo.getRepositoryUrl();
      svnRootUrl = rootUrlInfo.getUrl();
      svnRootLocation = new SvnRepositoryLocation(rootUrlInfo.getUrl().toString());
      repositoryRelativeUrl = SvnUtil.ensureStartSlash(SvnUtil.join(
        SvnUtil.getRelativeUrl(repositoryUrl, svnRootUrl),
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

  private boolean hasAccess(@NotNull Url url) {
    return SvnAuthenticationNotifier.passiveValidation(myVcs, url);
  }

  // return changed path, if any
  private FilePath searchFromHead(@NotNull Url url) throws VcsException {
    SvnCopyPathTracker pathTracker = new SvnCopyPathTracker(repositoryUrl, repositoryRelativeUrl);
    Target target = Target.on(url);

    myVcs.getFactory(target).createHistoryClient()
      .doLog(target, Revision.HEAD, revisionBefore, false, true, false, 0, null, logEntry -> {
        checkDisposed();
        // date could be null for lists where there are paths that user has no rights to observe
        if (logEntry.getDate() != null) {
          pathTracker.accept(logEntry);
          if (logEntry.getRevision() == revisionBefore.getNumber()) {
            changeList[0] = createChangeList(logEntry);
          }
        }
      }
    );

    FilePath path = pathTracker.getFilePath(myVcs);
    return path == null ? filePath : path;
  }

  @NotNull
  private SvnChangeList createChangeList(@NotNull LogEntry logEntry) {
    return new SvnChangeList(myVcs, svnRootLocation, logEntry, repositoryUrl);
  }

  private void checkDisposed() {
    if (myProject.isDisposed()) {
      throw new ProcessCanceledException();
    }
  }

  private boolean searchForUrl(@NotNull Url url) throws VcsException {
    LogEntryConsumer handler = logEntry -> {
      checkDisposed();
      // date could be null for lists where there are paths that user has no rights to observe
      if (logEntry.getDate() != null) {
        changeList[0] = createChangeList(logEntry);
      }
    };

    Target target = Target.on(url);
    try {
      myVcs.getFactory(target).createHistoryClient().doLog(target, revisionBefore, revisionBefore, false, true, false, 1, null, handler);
    }
    catch (SvnBindException e) {
      LOG.info(e);
      if (!e.containsCategory(ErrorCategory.FS)) {
        throw e;
      }
    }
    return changeList[0] != null;
  }
}
