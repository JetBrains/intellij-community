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

package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.FileContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.status.SvnDiffEditor;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class CompareWithBranchAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.actions.CompareWithBranchAction");

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    assert project != null;
    final VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);

    SelectBranchPopup.show(project, virtualFile, new SelectBranchPopup.BranchSelectedCallback() {
      public void branchSelected(final Project project, final SvnBranchConfigurationNew configuration, final String url, final long revision) {
        new CompareWithBranchOperation(project, virtualFile, configuration).compareWithBranch(url, revision);
      }
    }, SvnBundle.message("compare.with.branch.popup.title"));
  }

  @Override
  public void update(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    e.getPresentation().setEnabled(isEnabled(project, virtualFile));
  }

  private static boolean isEnabled(final Project project, final VirtualFile virtualFile) {
    if (project == null || virtualFile == null) {
      return false;
    }
    final FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(virtualFile);
    if (fileStatus == FileStatus.UNKNOWN || fileStatus == FileStatus.ADDED) {
      return false;
    }
    return true;
  }


  private class CompareWithBranchOperation {
    private final Project myProject;
    private final VirtualFile myVirtualFile;
    private final SvnBranchConfigurationNew myConfiguration;

    public CompareWithBranchOperation(final Project project, final VirtualFile virtualFile, final SvnBranchConfigurationNew config) {
      myProject = project;
      myVirtualFile = virtualFile;
      myConfiguration = config;
    }

    public void compareWithBranch(final String baseUrl, final long revision) {
      if (myVirtualFile.isDirectory()) {
        compareDirectoryWithBranch(baseUrl, revision);
      }
      else {
        compareFileWithBranch(baseUrl, revision);
      }
    }
    final StringBuilder titleBuilder = new StringBuilder();

    public void compareDirectoryWithBranch(final String baseUrl, final long revision) {
      final List<Change> changes = new ArrayList<Change>();
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          try {
            final SvnVcs vcs = SvnVcs.getInstance(myProject);
            final SVNURL url = getURLInBranch(vcs, baseUrl);
            if (url == null) return;
            titleBuilder.append(SvnBundle.message("repository.browser.compare.title",
                                                  url.toString(),
                                                  FileUtil.toSystemDependentName(myVirtualFile.getPresentableUrl())));


            SVNWCAccess wcAccess = vcs.createWCAccess();
            SVNRepository repository = null;
            SVNRepository repository2 = null;
            try {
              SVNAdminAreaInfo info = wcAccess.openAnchor(new File(myVirtualFile.getPath()), false, SVNWCAccess.INFINITE_DEPTH);
              File anchorPath = info.getAnchor().getRoot();
              String target = "".equals(info.getTargetName()) ? null : info.getTargetName();

              SVNEntry anchorEntry = info.getAnchor().getEntry("", false);
              if (anchorEntry == null) {
                SVNErrorMessage err =
                  SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", anchorPath);
                SVNErrorManager.error(err, SVNLogType.WC);
              }
              else if (anchorEntry.getURL() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", anchorPath);
                SVNErrorManager.error(err, SVNLogType.WC);
              }

              SVNURL anchorURL = anchorEntry.getSVNURL();
              repository = vcs.createRepository(anchorURL.toString());
              SVNReporter reporter = new SVNReporter(info, info.getAnchor().getFile(info.getTargetName()), false, true, SVNDepth.INFINITY, 
                                                     false, false, true, SVNDebugLog.getDefaultLog());
              long rev = repository.getLatestRevision();
              repository2 = vcs.createRepository((target == null) ? url.toString() : url.removePathTail().toString());
              SvnDiffEditor diffEditor = new SvnDiffEditor((target == null) ? myVirtualFile : myVirtualFile.getParent(),
                repository2, rev, true);
              repository.diff(url, rev, rev, target, true, true, false, reporter,
                              SVNCancellableEditor.newInstance(diffEditor, new SvnProgressCanceller(), null));
              changes.addAll(diffEditor.getChangesMap().values());
            }
            finally {
              wcAccess.close();
              if (repository != null) {
                repository.closeSession();
              }
              if (repository2 != null) {
                repository2.closeSession();
              }
            }
          }
          catch(SVNCancelException ex) {
            changes.clear();
          }
          catch (SVNException ex) {
            reportException(ex, baseUrl);
          }
        }
      }, SvnBundle.message("progress.computing.difference"), true, myProject);
      if (!changes.isEmpty()) {
        AbstractVcsHelper.getInstance(myProject).showWhatDiffersBrowser(null, changes, titleBuilder.toString());
      }
    }

    public void compareFileWithBranch(final String baseUrl, final long revision) {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      final StringBuilder remoteTitleBuilder = new StringBuilder();
      final Ref<Boolean> success = new Ref<Boolean>();
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          try {
            final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
            if (indicator != null) {
              indicator.setIndeterminate(true);
            }
            final SvnVcs vcs = SvnVcs.getInstance(myProject);
            SVNURL svnurl = getURLInBranch(vcs, baseUrl);
            if (svnurl == null) return;
            remoteTitleBuilder.append(svnurl.toString());
            SVNWCClient client = vcs.createWCClient();
            client.doGetFileContents(svnurl, SVNRevision.UNDEFINED, SVNRevision.HEAD, true, baos);
            success.set(true);
          }
          catch (SVNException ex) {
            reportException(ex, baseUrl);
          }
        }
      }, SvnBundle.message("compare.with.branch.progress.loading.content"), true, myProject);
      if (success.isNull()) {
        return;
      }
      ByteBuffer contents = ByteBuffer.wrap(baos.toByteArray());
      SimpleDiffRequest req = new SimpleDiffRequest(myProject, SvnBundle.message("compare.with.branch.diff.title"));
      req.setContents(new SimpleContent(myVirtualFile.getCharset().decode(contents).toString()),
                      new FileContent(myProject, myVirtualFile));
      req.setContentTitles(remoteTitleBuilder.toString(), myVirtualFile.getPresentableUrl());
      DiffManager.getInstance().getDiffTool().show(req);
    }

    @Nullable
    private SVNURL getURLInBranch(final SvnVcs vcs, final String baseUrl) throws SVNException {
      final SvnFileUrlMapping urlMapping = vcs.getSvnFileUrlMapping();
      final File file = new File(myVirtualFile.getPath());
      final SVNURL fileUrl = urlMapping.getUrlForFile(file);
      if (fileUrl == null) {
        return null;
      }

      final String fileUrlString = fileUrl.toString();
      final RootUrlInfo rootMixed = urlMapping.getWcRootForUrl(fileUrlString);
      if (rootMixed == null) {
        return null;
      }

      final SVNURL thisBranchForUrl = SvnUtil.getBranchForUrl(vcs, rootMixed.getVirtualFile(), fileUrlString);
      if (thisBranchForUrl == null) {
        return null;
      }

      final String relativePath = SVNPathUtil.getRelativePath(thisBranchForUrl.toString(), fileUrlString);
      return SVNURL.parseURIEncoded(SVNPathUtil.append(baseUrl, relativePath));
    }

    private void reportException(final SVNException ex, final String baseUrl) {
      final SVNErrorCode errorCode = ex.getErrorMessage().getErrorCode();
      if (errorCode.equals(SVNErrorCode.RA_ILLEGAL_URL) ||
          errorCode.equals(SVNErrorCode.CLIENT_UNRELATED_RESOURCES) ||
          errorCode.equals(SVNErrorCode.RA_DAV_PATH_NOT_FOUND) ||
          errorCode.equals(SVNErrorCode.FS_NOT_FOUND)) {
        reportNotFound(baseUrl);
      }
      else {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showMessageDialog(myProject, ex.getMessage(),
                                       SvnBundle.message("compare.with.branch.error.title"), Messages.getErrorIcon());
          }
        });
        LOG.info(ex);
      }
    }

    private void reportNotFound(final String baseUrl) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Messages.showMessageDialog(myProject,
                                     SvnBundle.message("compare.with.branch.location.error", myVirtualFile.getPresentableUrl(), baseUrl),
                                     SvnBundle.message("compare.with.branch.error.title"), Messages.getErrorIcon());
        }
      });
    }
  }
}
