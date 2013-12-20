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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.FileContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.status.SvnDiffEditor;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.internal.wc17.SVNReporter17;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class CompareWithBranchAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.actions.CompareWithBranchAction");

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    assert project != null;
    final VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);

    SelectBranchPopup
      .show(project, virtualFile, new MyBranchSelectedCallback(virtualFile), SvnBundle.message("compare.with.branch.popup.title"));
  }

  @Override
  public void update(final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    e.getPresentation().setEnabled(isEnabled(project, virtualFile));
  }

  private static boolean isEnabled(final Project project, final VirtualFile virtualFile) {
    if (project == null || virtualFile == null) {
      return false;
    }
    final FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(virtualFile);
    if (fileStatus == FileStatus.UNKNOWN || fileStatus == FileStatus.ADDED || fileStatus == FileStatus.IGNORED) {
      return false;
    }
    return true;
  }

  private static class MyBranchSelectedCallback implements SelectBranchPopup.BranchSelectedCallback {

    @NotNull private final VirtualFile myVirtualFile;

    public MyBranchSelectedCallback(@NotNull VirtualFile virtualFile) {
      myVirtualFile = virtualFile;
    }

    public void branchSelected(Project project, SvnBranchConfigurationNew configuration, String url, long revision) {
      ElementWithBranchComparer comparer =
        myVirtualFile.isDirectory()
        ? new DirectoryWithBranchComparer(project, myVirtualFile, url, revision)
        : new FileWithBranchComparer(project, myVirtualFile, url, revision);

      comparer.run();
    }
  }

  private static abstract class ElementWithBranchComparer {

    @NotNull protected final Project myProject;
    @NotNull protected final SvnVcs myVcs;
    @NotNull protected final VirtualFile myVirtualFile;
    @NotNull protected final String myBranchUrl;
    protected final long myBranchRevision;
    protected SVNURL myElementUrl;

    protected ElementWithBranchComparer(@NotNull Project project,
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

    protected abstract void compare() throws SVNException, VcsException;

    protected abstract void showResult();

    protected void onCancel() {
    }

    public abstract String getTitle();

    @Nullable
    protected SVNURL resolveElementUrl() throws SVNException {
      final SvnFileUrlMapping urlMapping = myVcs.getSvnFileUrlMapping();
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

    private void reportGeneralException(final Exception e) {
      WaitForProgressToShow.runOrInvokeLaterAboveProgress(new Runnable() {
        public void run() {
          Messages.showMessageDialog(myProject, e.getMessage(),
                                     SvnBundle.message("compare.with.branch.error.title"), Messages.getErrorIcon());
        }
      }, null, myProject);
      LOG.info(e);
    }

    private void reportNotFound() {
      WaitForProgressToShow.runOrInvokeLaterAboveProgress(new Runnable() {
        public void run() {
          Messages.showMessageDialog(myProject,
                                     SvnBundle
                                       .message("compare.with.branch.location.error", myVirtualFile.getPresentableUrl(), myBranchUrl),
                                     SvnBundle.message("compare.with.branch.error.title"), Messages.getErrorIcon());
        }
      }, null, myProject);
    }
  }

  public static class FileWithBranchComparer extends ElementWithBranchComparer {

    @NotNull private final Ref<byte[]> content = new Ref<byte[]>();
    @NotNull private final StringBuilder remoteTitleBuilder = new StringBuilder();
    @NotNull private final Ref<Boolean> success = new Ref<Boolean>();

    public FileWithBranchComparer(@NotNull Project project,
                                  @NotNull VirtualFile virtualFile,
                                  @NotNull String branchUrl,
                                  long branchRevision) {
      super(project, virtualFile, branchUrl, branchRevision);
    }

    @Override
    protected void beforeCompare() {
      final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null) {
        indicator.setIndeterminate(true);
      }
    }

    @Override
    protected void compare() throws SVNException, VcsException {
      remoteTitleBuilder.append(myElementUrl);
      content.set(SvnUtil.getFileContents(myVcs, SvnTarget.fromURL(myElementUrl), SVNRevision.HEAD, SVNRevision.UNDEFINED));
      success.set(true);
    }

    @Override
    protected void showResult() {
      if (!success.isNull()) {
        SimpleDiffRequest req = new SimpleDiffRequest(myProject, SvnBundle.message("compare.with.branch.diff.title"));
        req.setContents(new SimpleContent(CharsetToolkit.bytesToString(content.get(), myVirtualFile.getCharset())),
                        new FileContent(myProject, myVirtualFile));
        req.setContentTitles(remoteTitleBuilder.toString(), myVirtualFile.getPresentableUrl());
        DiffManager.getInstance().getDiffTool().show(req);
      }
    }

    @Override
    public String getTitle() {
      return SvnBundle.message("compare.with.branch.progress.loading.content");
    }
  }

  public static class DirectoryWithBranchComparer extends ElementWithBranchComparer {

    @NotNull private final StringBuilder titleBuilder = new StringBuilder();
    @NotNull private final List<Change> changes = new ArrayList<Change>();

    public DirectoryWithBranchComparer(@NotNull Project project,
                                       @NotNull VirtualFile virtualFile,
                                       @NotNull String branchUrl,
                                       long branchRevision) {
      super(project, virtualFile, branchUrl, branchRevision);
    }

    @Override
    protected void compare() throws SVNException, VcsException {
      titleBuilder.append(SvnBundle.message("repository.browser.compare.title", myElementUrl,
                                            FileUtil.toSystemDependentName(myVirtualFile.getPresentableUrl())));

      final File ioFile = new File(myVirtualFile.getPath());
      if (SvnUtil.is17CopyPart(ioFile)) {
        report17DirDiff();
      }
      else {
        report16DirDiff();
      }
    }

    private void report17DirDiff() throws SVNException {
      final File ioFile = new File(myVirtualFile.getPath());
      final SVNWCClient wcClient = myVcs.createWCClient();
      final SVNInfo info1 = wcClient.doInfo(ioFile, SVNRevision.HEAD);

      if (info1 == null) {
        SVNErrorMessage err =
          SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", myVirtualFile.getPath());
        SVNErrorManager.error(err, SVNLogType.WC);
      }
      else if (info1.getURL() == null) {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", myVirtualFile.getPath());
        SVNErrorManager.error(err, SVNLogType.WC);
      }

      final SVNReporter17 reporter17 =
        new SVNReporter17(ioFile, new SVNWCContext(SvnConfiguration.getInstance(myProject).getOptions(myProject), new ISVNEventHandler() {
          @Override
          public void handleEvent(SVNEvent event, double progress) throws SVNException {
          }

          @Override
          public void checkCancelled() throws SVNCancelException {
          }
        }),
                          false, true, SVNDepth.INFINITY, false, false, true, false,
                          SVNDebugLog.getDefaultLog());
      SVNRepository repository = null;
      SVNRepository repository2 = null;
      try {
        repository = myVcs.createRepository(info1.getURL());
        long rev = repository.getLatestRevision();
        repository2 = myVcs.createRepository(myElementUrl.toString());
        SvnDiffEditor diffEditor = new SvnDiffEditor(myVirtualFile, repository2, rev, true);
        repository.diff(myElementUrl, rev, rev, null, true, SVNDepth.INFINITY, false, reporter17,
                        SVNCancellableEditor.newInstance(diffEditor, new SvnProgressCanceller(), null));
        changes.addAll(diffEditor.getChangesMap().values());
      }
      finally {
        if (repository != null) {
          repository.closeSession();
        }
        if (repository2 != null) {
          repository2.closeSession();
        }
      }
    }

    private void report16DirDiff() throws SVNException {
      // here there's 1.6 copy so ok to use SVNWCAccess
      final SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
      wcAccess.setOptions(myVcs.getSvnOptions());
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
        SVNReporter reporter = new SVNReporter(info, info.getAnchor().getFile(info.getTargetName()), false, true, SVNDepth.INFINITY,
                                               false, false, true, SVNDebugLog.getDefaultLog());

        repository = myVcs.createRepository(anchorURL.toString());
        long rev = repository.getLatestRevision();
        repository2 = myVcs.createRepository((target == null) ? myElementUrl.toString() : myElementUrl.removePathTail().toString());
        SvnDiffEditor diffEditor = new SvnDiffEditor((target == null) ? myVirtualFile : myVirtualFile.getParent(),
                                                     repository2, rev, true);
        repository.diff(myElementUrl, rev, rev, target, true, true, false, reporter,
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

    @Override
    protected void onCancel() {
      changes.clear();
    }

    @Override
    protected void showResult() {
      if (!changes.isEmpty()) {
        AbstractVcsHelper.getInstance(myProject).showWhatDiffersBrowser(null, changes, titleBuilder.toString());
      }
    }

    @Override
    public String getTitle() {
      return SvnBundle.message("progress.computing.difference");
    }
  }
}
