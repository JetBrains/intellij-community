// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.diff;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.api.ErrorCode;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.io.File;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;

/**
* @author Konstantin Kolosovsky.
*/
public abstract class ElementWithBranchComparer {

  private static final Logger LOG = Logger.getInstance(ElementWithBranchComparer.class);

  @NotNull protected final Project myProject;
  @NotNull protected final SvnVcs myVcs;
  @NotNull protected final VirtualFile myVirtualFile;
  @NotNull protected final String myBranchUrl;
  protected final long myBranchRevision;
  protected Url myElementUrl;

  ElementWithBranchComparer(@NotNull Project project,
                            @NotNull VirtualFile virtualFile,
                            @NotNull String branchUrl,
                            long branchRevision) {
    myProject = project;
    myVcs = SvnVcs.getInstance(myProject);
    myVirtualFile = virtualFile;
    myBranchUrl = branchUrl;
    myBranchRevision = branchRevision;
  }

  public void run() {
    new Task.Modal(myProject, getTitle(), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          beforeCompare();
          myElementUrl = resolveElementUrl();
          if (myElementUrl == null) {
            reportNotFound();
          }
          else {
            compare();
          }
        }
        catch (SvnBindException ex) {
          reportException(ex);
        }
        catch (VcsException ex) {
          reportGeneralException(ex);
        }
      }
    }.queue();
    showResult();
  }

  protected void beforeCompare() {
  }

  protected abstract void compare() throws VcsException;

  protected abstract void showResult();

  public abstract String getTitle();

  @Nullable
  protected Url resolveElementUrl() throws SvnBindException {
    final SvnFileUrlMapping urlMapping = myVcs.getSvnFileUrlMapping();
    final File file = virtualToIoFile(myVirtualFile);
    final Url fileUrl = urlMapping.getUrlForFile(file);
    if (fileUrl == null) {
      return null;
    }

    final RootUrlInfo rootMixed = urlMapping.getWcRootForUrl(fileUrl);
    if (rootMixed == null) {
      return null;
    }

    final Url thisBranchForUrl = SvnUtil.getBranchForUrl(myVcs, rootMixed.getVirtualFile(), fileUrl);
    if (thisBranchForUrl == null) {
      return null;
    }

    String relativePath = SvnUtil.getRelativeUrl(thisBranchForUrl, fileUrl);
    return createUrl(Url.append(myBranchUrl, relativePath));
  }

  private void reportException(final SvnBindException e) {
    if (e.contains(ErrorCode.RA_ILLEGAL_URL) ||
        e.contains(ErrorCode.CLIENT_UNRELATED_RESOURCES) ||
        e.contains(ErrorCode.RA_DAV_PATH_NOT_FOUND) ||
        e.contains(ErrorCode.FS_NOT_FOUND) ||
        e.contains(ErrorCode.ILLEGAL_TARGET)) {
      reportNotFound();
    }
    else {
      reportGeneralException(e);
    }
  }

  protected void reportGeneralException(final Exception e) {
    WaitForProgressToShow.runOrInvokeLaterAboveProgress(
      () -> Messages
        .showMessageDialog(myProject, e.getMessage(), SvnBundle.message("compare.with.branch.error.title"), Messages.getErrorIcon()), null,
      myProject);
    LOG.info(e);
  }

  private void reportNotFound() {
    WaitForProgressToShow.runOrInvokeLaterAboveProgress(() -> Messages
      .showMessageDialog(myProject, SvnBundle.message("compare.with.branch.location.error", myVirtualFile.getPresentableUrl(), myBranchUrl),
                         SvnBundle.message("compare.with.branch.error.title"), Messages.getErrorIcon()), null, myProject);
  }
}
