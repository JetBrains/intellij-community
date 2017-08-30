/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.io.File;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

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
  protected SVNURL myElementUrl;

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
        catch (SVNCancelException ex) {
          ElementWithBranchComparer.this.onCancel();
        }
        catch (SVNException ex) {
          reportException(new SvnBindException(ex));
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

  protected void onCancel() {
  }

  public abstract String getTitle();

  @Nullable
  protected SVNURL resolveElementUrl() throws SVNException {
    final SvnFileUrlMapping urlMapping = myVcs.getSvnFileUrlMapping();
    final File file = virtualToIoFile(myVirtualFile);
    final SVNURL fileUrl = urlMapping.getUrlForFile(file);
    if (fileUrl == null) {
      return null;
    }

    final String fileUrlString = fileUrl.toString();
    final RootUrlInfo rootMixed = urlMapping.getWcRootForUrl(fileUrlString);
    if (rootMixed == null) {
      return null;
    }

    final SVNURL thisBranchForUrl = SvnUtil.getBranchForUrl(myVcs, rootMixed.getVirtualFile(), fileUrlString);
    if (thisBranchForUrl == null) {
      return null;
    }

    final String relativePath = SVNPathUtil.getRelativePath(thisBranchForUrl.toString(), fileUrlString);
    return SVNURL.parseURIEncoded(SVNPathUtil.append(myBranchUrl, relativePath));
  }

  private void reportException(final SvnBindException e) {
    if (e.contains(SVNErrorCode.RA_ILLEGAL_URL) ||
        e.contains(SVNErrorCode.CLIENT_UNRELATED_RESOURCES) ||
        e.contains(SVNErrorCode.RA_DAV_PATH_NOT_FOUND) ||
        e.contains(SVNErrorCode.FS_NOT_FOUND) ||
        e.contains(SVNErrorCode.ILLEGAL_TARGET)) {
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
