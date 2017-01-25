/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
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

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class ShareProjectAction extends BasicAction {

  @NotNull
  @Override
  protected String getActionName() {
    return SvnBundle.message("share.directory.action");
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    DataContext dataContext = e.getDataContext();

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

  @Override
  protected boolean isEnabled(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    return false;
  }

  public static boolean share(@NotNull Project project, VirtualFile file) throws VcsException {
    return performImpl(project, SvnVcs.getInstance(project), file);
  }

  @Override
  protected void perform(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) throws VcsException {
    performImpl(vcs.getProject(), vcs, file);
  }

  private static boolean performImpl(Project project, SvnVcs activeVcs, VirtualFile file) throws VcsException {
    ShareDialog shareDialog = new ShareDialog(project, file.getName());
    shareDialog.show();

    String parent = shareDialog.getSelectedURL();
    if (shareDialog.isOK() && parent != null) {
      Ref<Boolean> actionStarted = new Ref<>(Boolean.TRUE);
      Exception[] error = new Exception[1];
      ShareDialog.ShareTarget shareTarget = shareDialog.getShareTarget();
      ProgressManager progressManager = ProgressManager.getInstance();

      if (ShareDialog.ShareTarget.useSelected.equals(shareTarget)) {
        boolean folderEmpty = checkRemoteFolder(project, activeVcs, parent, progressManager);

        if (!folderEmpty) {
          int promptAnswer =
            Messages.showYesNoDialog(project, "Remote folder \"" + parent + "\" is not empty.\nDo you want to continue sharing?",
                                     "Share directory", Messages.getWarningIcon());
          if (Messages.YES != promptAnswer) return false;
        }
      }

      WorkingCopyFormat format = SvnCheckoutProvider.promptForWCopyFormat(virtualToIoFile(file), project);
      actionStarted.set(format != WorkingCopyFormat.UNKNOWN);
      // means operation cancelled
      if (format == WorkingCopyFormat.UNKNOWN) {
        return true;
      }

      ExclusiveBackgroundVcsAction.run(project, () -> progressManager.runProcessWithProgressSynchronously(() -> {
        try {
          ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
          File path = virtualToIoFile(file);

          SvnWorkingCopyFormatHolder.setPresetFormat(format);

          SVNURL parenUrl = SVNURL.parseURIEncoded(parent);
          SVNURL checkoutUrl;
          SVNRevision revision;
          String commitText = shareDialog.getCommitText();
          if (ShareDialog.ShareTarget.useSelected.equals(shareTarget)) {
            checkoutUrl = parenUrl;
            revision = SVNRevision.HEAD;
          }
          else if (ShareDialog.ShareTarget.useProjectName.equals(shareTarget)) {
            Pair<SVNRevision, SVNURL> pair = createRemoteFolder(activeVcs, parenUrl, file.getName(), commitText);
            revision = pair.getFirst();
            checkoutUrl = pair.getSecond();
          }
          else {
            Pair<SVNRevision, SVNURL> pair = createRemoteFolder(activeVcs, parenUrl, file.getName(), commitText);
            Pair<SVNRevision, SVNURL> trunkPair = createRemoteFolder(activeVcs, pair.getSecond(), "trunk", commitText);
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

          ClientFactory factory = SvnCheckoutProvider.getFactory(activeVcs, format);

          factory.createCheckoutClient()
            .checkout(SvnTarget.fromURL(checkoutUrl), path, revision, Depth.INFINITY, false, false, format, null);
          addRecursively(activeVcs, factory, file);
        }
        catch (SVNException | VcsException e) {
          error[0] = e;
        }
        finally {
          activeVcs.invokeRefreshSvnRoots();
          SvnWorkingCopyFormatHolder.setPresetFormat(null);
        }
      }, SvnBundle.message("share.directory.title"), true, project));

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

  private static boolean checkRemoteFolder(Project project, SvnVcs activeVcs, String parent, ProgressManager progressManager)
    throws VcsException {
    VcsException[] exc = new VcsException[1];
    boolean[] folderEmpty = new boolean[1];

    progressManager.runProcessWithProgressSynchronously(() -> {
      try {
        folderEmpty[0] = SvnUtil.remoteFolderIsEmpty(activeVcs, parent);
      }
      catch (VcsException e) {
        exc[0] = e;
      }
    }, "Check remote folder contents", false, project);
    if (exc[0] != null) {
      throw exc[0];
    }
    return folderEmpty[0];
  }

  @NotNull
  private static Pair<SVNRevision, SVNURL> createRemoteFolder(@NotNull SvnVcs vcs,
                                                              @NotNull SVNURL parent,
                                                              String folderName,
                                                              String commitText) throws VcsException, SVNException {
    SVNURL url = parent.appendPath(folderName, false);
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.checkCanceled();
      indicator.setText(SvnBundle.message("share.directory.create.dir.progress.text", url.toString()));
    }

    String message =
      SvnBundle.message("share.directory.commit.message", folderName, ApplicationNamesInfo.getInstance().getFullProductName(), commitText);
    SvnTarget target = SvnTarget.fromURL(url);
    long revision = vcs.getFactoryFromSettings().createBrowseClient().createDirectory(target, message, false);

    return Pair.create(SVNRevision.create(revision), url);
  }

  @Override
  protected void doVcsRefresh(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    VcsDirtyScopeManager.getInstance(vcs.getProject()).dirDirtyRecursively(file);
  }

  private static void addRecursively(@NotNull SvnVcs vcs, @NotNull ClientFactory factory, @NotNull VirtualFile file) throws VcsException {
    SvnExcludingIgnoredOperation operation = new SvnExcludingIgnoredOperation(vcs.getProject(), virtualFile -> {
      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null) {
        indicator.checkCanceled();
        indicator.setText(SvnBundle.message("share.or.import.add.progress.text", virtualFile.getPath()));
      }
      factory.createAddClient().add(virtualToIoFile(virtualFile), Depth.EMPTY, false, false, true, null);
    }, Depth.INFINITY);

    operation.execute(file);
  }

  @Override
  protected void batchPerform(@NotNull SvnVcs vcs, @NotNull VirtualFile[] files, @NotNull DataContext context) throws VcsException {
  }

  protected boolean isBatchAction() {
    return false;
  }
}
