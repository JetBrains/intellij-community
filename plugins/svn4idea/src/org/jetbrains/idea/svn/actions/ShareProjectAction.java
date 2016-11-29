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
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.api.ClientFactory;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.jetbrains.idea.svn.dialogs.ShareDialog;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

public class ShareProjectAction extends BasicAction {

  protected String getActionName(AbstractVcs vcs) {
    return SvnBundle.message("share.directory.action");
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    final DataContext dataContext = e.getDataContext();

    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if ((project == null) || (ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning())) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    if (files == null || files.length == 0) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    boolean enabled = false;
    boolean visible = false;
    if (files.length == 1 && files [0].isDirectory()) {
      visible = true;
      if (! SvnStatusUtil.isUnderControl(project, files[0])) {
        enabled = true;
      }
    }
    presentation.setEnabled(enabled);
    presentation.setVisible(visible);
  }

  protected boolean isEnabled(Project project, SvnVcs vcs, VirtualFile file) {
    return false;
  }

  protected boolean needsFiles() {
    return true;
  }

  public static boolean share(final Project project, final VirtualFile file) throws VcsException {
    return performImpl(project, SvnVcs.getInstance(project), file);
  }

  protected void perform(final Project project, final SvnVcs activeVcs, final VirtualFile file, DataContext context) throws VcsException {
    performImpl(project, activeVcs, file);
  }

  private static boolean performImpl(final Project project, final SvnVcs activeVcs, final VirtualFile file) throws VcsException {
    final ShareDialog shareDialog = new ShareDialog(project, file.getName());
    shareDialog.show();

    final String parent = shareDialog.getSelectedURL();
    if (shareDialog.isOK() && parent != null) {
      final Ref<Boolean> actionStarted = new Ref<>(Boolean.TRUE);
      final Exception[] error = new Exception[1];

      final ShareDialog.ShareTarget shareTarget = shareDialog.getShareTarget();
      final ProgressManager progressManager = ProgressManager.getInstance();

      if (ShareDialog.ShareTarget.useSelected.equals(shareTarget)) {
        final boolean folderEmpty = checkRemoteFolder(project, activeVcs, parent, progressManager);

        if (! folderEmpty) {
          final int promptAnswer =
            Messages.showYesNoDialog(project, "Remote folder \"" + parent + "\" is not empty.\nDo you want to continue sharing?",
                                     "Share directory", Messages.getWarningIcon());
          if (Messages.YES != promptAnswer) return false;
        }
      }

      final WorkingCopyFormat format = SvnCheckoutProvider.promptForWCopyFormat(VfsUtilCore.virtualToIoFile(file), project);
      actionStarted.set(format != WorkingCopyFormat.UNKNOWN);
      // means operation cancelled
      if (format == WorkingCopyFormat.UNKNOWN) {
        return true;
      }

      ExclusiveBackgroundVcsAction.run(project, new Runnable() {
        public void run() {
          progressManager.runProcessWithProgressSynchronously(new Runnable() {
            public void run() {
              try {
                final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
                final File path = new File(file.getPath());

                SvnWorkingCopyFormatHolder.setPresetFormat(format);

                final SVNURL parenUrl = SVNURL.parseURIEncoded(parent);
                final SVNURL checkoutUrl;
                final SVNRevision revision;
                final String commitText = shareDialog.getCommitText();
                if (ShareDialog.ShareTarget.useSelected.equals(shareTarget)) {
                  checkoutUrl = parenUrl;
                  revision = SVNRevision.HEAD;
                } else if (ShareDialog.ShareTarget.useProjectName.equals(shareTarget)) {
                  final Pair<SVNRevision, SVNURL> pair = createRemoteFolder(activeVcs, parenUrl, file.getName(), commitText);
                  revision = pair.getFirst();
                  checkoutUrl = pair.getSecond();
                } else {
                  final Pair<SVNRevision, SVNURL> pair = createRemoteFolder(activeVcs, parenUrl, file.getName(), commitText);
                  final Pair<SVNRevision, SVNURL> trunkPair = createRemoteFolder(activeVcs, pair.getSecond(), "trunk", commitText);
                  checkoutUrl = trunkPair.getSecond();
                  revision = trunkPair.getFirst();

                  if (shareDialog.createStandardStructure()) {
                    createRemoteFolder(activeVcs, pair.getSecond(), "branches", commitText);
                    createRemoteFolder(activeVcs, pair.getSecond(), "tags", commitText);
                  }
                }

                if (indicator != null) {
                  indicator.checkCanceled();
                  indicator.setText(SvnBundle.message("share.directory.checkout.back.progress.text", checkoutUrl.toString()));
                }

                final ClientFactory factory = SvnCheckoutProvider.getFactory(activeVcs, format);

                factory.createCheckoutClient()
                  .checkout(SvnTarget.fromURL(checkoutUrl), path, revision, Depth.INFINITY, false, false, format, null);
                addRecursively(activeVcs, factory, file);
              } catch (SVNException e) {
                error[0] = e;
              }
              catch (VcsException e) {
                error[0] = e;
              } finally {
                activeVcs.invokeRefreshSvnRoots();
                SvnWorkingCopyFormatHolder.setPresetFormat(null);
              }
            }
          }, SvnBundle.message("share.directory.title"), true, project);
        }
      });

      if (Boolean.TRUE.equals(actionStarted.get())) {
        if (error[0] != null) {
          throw new VcsException(error[0].getMessage());
        }
        Messages.showInfoMessage(project, SvnBundle.message("share.directory.info.message", file.getName()),
                                 SvnBundle.message("share.directory.title"));
      }
      return true;
    }
    return false;
  }

  private static boolean checkRemoteFolder(Project project,
                                        final SvnVcs activeVcs,
                                        final String parent,
                                        ProgressManager progressManager) throws VcsException {
    final VcsException[] exc = new VcsException[1];
    final boolean[] folderEmpty = new boolean[1];

    progressManager.runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        try {
          folderEmpty[0] = SvnUtil.remoteFolderIsEmpty(activeVcs, parent);
        }
        catch (VcsException e) {
          exc[0] = e;
        }
      }
    }, "Check remote folder contents", false, project);
    if (exc[0] != null) {
      throw exc[0];
    }
    return folderEmpty[0];
  }

  private static Pair<SVNRevision, SVNURL> createRemoteFolder(@NotNull final SvnVcs vcs,
                                                              @NotNull final SVNURL parent,
                                                              final String folderName,
                                                              String commitText) throws VcsException, SVNException {
    SVNURL url = parent.appendPath(folderName, false);
    final String urlText = url.toString();
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.checkCanceled();
      indicator.setText(SvnBundle.message("share.directory.create.dir.progress.text", urlText));
    }

    String message =
      SvnBundle.message("share.directory.commit.message", folderName, ApplicationNamesInfo.getInstance().getFullProductName(), commitText);
    SvnTarget target = SvnTarget.fromURL(url);
    long revision = vcs.getFactoryFromSettings().createBrowseClient().createDirectory(target, message, false);

    return Pair.create(SVNRevision.create(revision), url);
  }

  @Override
  protected void doVcsRefresh(final Project project, final VirtualFile file) {
    VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(file);
  }

  private static void addRecursively(@NotNull final SvnVcs activeVcs, @NotNull final ClientFactory factory, final VirtualFile file)
    throws VcsException {
    final SvnExcludingIgnoredOperation operation = new SvnExcludingIgnoredOperation(activeVcs.getProject(), new SvnExcludingIgnoredOperation.Operation() {
      public void doOperation(final VirtualFile virtualFile) throws VcsException {
        final File ioFile = new File(virtualFile.getPath());
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          indicator.checkCanceled();
          indicator.setText(SvnBundle.message("share.or.import.add.progress.text", virtualFile.getPath()));
        }
        factory.createAddClient().add(ioFile, Depth.EMPTY, false, false, true, null);
      }
    }, Depth.INFINITY);

    operation.execute(file);
  }

  protected void batchPerform(Project project, final SvnVcs activeVcs, VirtualFile[] file, DataContext context) throws VcsException {
  }

  protected boolean isBatchAction() {
    return false;
  }
}
