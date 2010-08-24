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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnStatusUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.SvnWorkingCopyFormatHolder;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.jetbrains.idea.svn.dialogs.ShareDialog;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;

public class ShareProjectAction extends BasicAction {

  protected String getActionName(AbstractVcs vcs) {
    return SvnBundle.message("share.directory.action");
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    final DataContext dataContext = e.getDataContext();

    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if ((project == null) || (ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning())) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
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
      final Ref<Boolean> actionStarted = new Ref<Boolean>(Boolean.TRUE);
      final SVNException[] error = new SVNException[1];

      ExclusiveBackgroundVcsAction.run(project, new Runnable() {
        public void run() {
          ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
            public void run() {
              try {
                final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

                final File path = new File(file.getPath());
                if (! SvnCheckoutProvider.promptForWCFormatAndSelect(path, project)) {
                  // action cancelled
                  actionStarted.set(Boolean.FALSE);
                  return;
                }
                final SVNURL parenUrl = SVNURL.parseURIEncoded(parent);
                final SVNURL checkoutUrl;
                final SVNRevision revision;
                
                final ShareDialog.ShareTarget shareTarget = shareDialog.getShareTarget();
                if (ShareDialog.ShareTarget.useSelected.equals(shareTarget)) {
                  checkoutUrl = parenUrl;
                  revision = SVNRevision.HEAD;
                } else if (ShareDialog.ShareTarget.useProjectName.equals(shareTarget)) {
                  final Pair<SVNRevision, SVNURL> pair = createRemoteFolder(activeVcs, parenUrl, file.getName());
                  revision = pair.getFirst();
                  checkoutUrl = pair.getSecond();
                } else {
                  final Pair<SVNRevision, SVNURL> pair = createRemoteFolder(activeVcs, parenUrl, file.getName());
                  final Pair<SVNRevision, SVNURL> trunkPair = createRemoteFolder(activeVcs, pair.getSecond(), "trunk");
                  checkoutUrl = trunkPair.getSecond();
                  revision = trunkPair.getFirst();

                  if (shareDialog.createStandardStructure()) {
                    createRemoteFolder(activeVcs, pair.getSecond(), "branches");
                    createRemoteFolder(activeVcs, pair.getSecond(), "tags");
                  }
                }

                if (indicator != null) {
                  indicator.checkCanceled();
                  indicator.setText(SvnBundle.message("share.directory.checkout.back.progress.text", checkoutUrl.toString()));
                }
                activeVcs.createUpdateClient().doCheckout(checkoutUrl, path, SVNRevision.UNDEFINED, revision, true);
                SvnWorkingCopyFormatHolder.setPresetFormat(null);

                addRecursively(activeVcs, file);
              } catch (SVNException e) {
                error[0] = e;
              } finally {
                activeVcs.invokeRefreshSvnRoots(true);
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

  private static Pair<SVNRevision, SVNURL> createRemoteFolder(final SvnVcs vcs, final SVNURL parent, final String folderName) throws SVNException {
    SVNURL url = parent.appendPath(folderName, false);
    final String urlText = url.toString();
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.checkCanceled();
      indicator.setText(SvnBundle.message("share.directory.create.dir.progress.text", urlText));
    }
    final SVNCommitInfo info =
      vcs.createCommitClient().doMkDir(new SVNURL[]{url}, SvnBundle.message("share.directory.commit.message", folderName,
                                                                            ApplicationNamesInfo.getInstance().getFullProductName()));
    return new Pair<SVNRevision, SVNURL>(SVNRevision.create(info.getNewRevision()), url);
  }

  @Override
  protected void doVcsRefresh(final Project project, final VirtualFile file) {
    VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(file);
  }

  private static void addRecursively(final SvnVcs activeVcs, final VirtualFile file) throws SVNException {
    final SVNWCClient wcClient = activeVcs.createWCClient();
    final SvnExcludingIgnoredOperation operation = new SvnExcludingIgnoredOperation(activeVcs.getProject(), new SvnExcludingIgnoredOperation.Operation() {
      public void doOperation(final VirtualFile virtualFile) throws SVNException {
        final File ioFile = new File(virtualFile.getPath());
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          indicator.checkCanceled();
          indicator.setText(SvnBundle.message("share.or.import.add.progress.text", virtualFile.getPath()));
        }
        wcClient.doAdd(ioFile, true, false, false, SVNDepth.EMPTY, false, false);
      }
    }, SVNDepth.INFINITY);

    operation.execute(file);
  }

  protected void batchPerform(Project project, final SvnVcs activeVcs, VirtualFile[] file, DataContext context) throws VcsException {
  }

  protected boolean isBatchAction() {
    return false;
  }
}
