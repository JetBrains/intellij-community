// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnStatusUtil;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.jetbrains.idea.svn.dialogs.ShareDialog;

import static com.intellij.openapi.progress.ProgressManager.progress;
import static com.intellij.openapi.ui.Messages.*;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ArrayUtil.isEmpty;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.append;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;

public class ShareProjectAction extends BasicAction {

  @NotNull
  @Override
  protected String getActionName() {
    return message("share.directory.action");
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    Project project = e.getProject();
    boolean visible = project != null &&
                      !ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning() &&
                      !isEmpty(files) &&
                      files.length == 1 &&
                      files[0].isDirectory();

    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(visible && !SvnStatusUtil.isUnderControl(project, files[0]));
  }

  @Override
  protected boolean isEnabled(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    return false;
  }

  public static boolean share(@NotNull Project project, @NotNull VirtualFile file) throws VcsException {
    return performImpl(SvnVcs.getInstance(project), file);
  }

  @Override
  protected void perform(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) throws VcsException {
    performImpl(vcs, file);
  }

  private static boolean performImpl(@NotNull SvnVcs vcs, @NotNull VirtualFile file) throws VcsException {
    ShareDialog shareDialog = new ShareDialog(vcs.getProject(), file.getName());
    shareDialog.show();

    String parent = shareDialog.getSelectedURL();
    if (shareDialog.isOK() && parent != null) {
      Ref<Boolean> actionStarted = new Ref<>(Boolean.TRUE);
      Exception[] error = new Exception[1];
      ShareDialog.ShareTarget shareTarget = shareDialog.getShareTarget();

      if (ShareDialog.ShareTarget.useSelected.equals(shareTarget) &&
          !isFolderEmpty(vcs, parent) &&
          YES !=
          showYesNoDialog(vcs.getProject(), "Remote folder \"" + parent + "\" is not empty.\nDo you want to continue sharing?",
                          "Share Directory", getWarningIcon())) {
        return false;
      }

      WorkingCopyFormat format = SvnCheckoutProvider.promptForWCopyFormat(virtualToIoFile(file), vcs.getProject());
      actionStarted.set(format != WorkingCopyFormat.UNKNOWN);
      // means operation cancelled
      if (format == WorkingCopyFormat.UNKNOWN) {
        return true;
      }

      ExclusiveBackgroundVcsAction.run(vcs.getProject(), () ->
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
          try {
            Target checkoutTarget =
              createFolderStructure(vcs, file, shareTarget, shareDialog.createStandardStructure(), createUrl(parent),
                                    shareDialog.getCommitText());

            progress(message("share.directory.checkout.back.progress.text", checkoutTarget.getPath()));

            ClientFactory factory = SvnCheckoutProvider.getFactory(vcs);

            factory.createCheckoutClient()
              .checkout(Target.on(checkoutTarget.getUrl()), virtualToIoFile(file), checkoutTarget.getPegRevision(), Depth.INFINITY,
                        false, false, format, null);
            addRecursively(vcs, factory, file);
          }
          catch (VcsException e) {
            error[0] = e;
          }
          finally {
            vcs.invokeRefreshSvnRoots();
          }
        }, message("share.directory.title"), true, vcs.getProject()));

      if (Boolean.TRUE.equals(actionStarted.get())) {
        if (error[0] != null) {
          throw new VcsException(error[0].getMessage());
        }
        showInfoMessage(vcs.getProject(), message("share.directory.info.message", file.getName()), message("share.directory.title"));
      }
      return true;
    }
    return false;
  }

  @NotNull
  private static Target createFolderStructure(@NotNull SvnVcs vcs,
                                              @NotNull VirtualFile file,
                                              @NotNull ShareDialog.ShareTarget shareTarget,
                                              boolean createStandardStructure,
                                              @NotNull Url parentUrl,
                                              @NotNull String commitText) throws VcsException {
    switch (shareTarget) {
      case useSelected:
        return Target.on(parentUrl, Revision.HEAD);
      case useProjectName:
        return createRemoteFolder(vcs, parentUrl, file.getName(), commitText);
      default:
        Target projectRoot = createRemoteFolder(vcs, parentUrl, file.getName(), commitText);
        Target trunk = createRemoteFolder(vcs, projectRoot.getUrl(), "trunk", commitText);

        if (createStandardStructure) {
          createRemoteFolder(vcs, projectRoot.getUrl(), "branches", commitText);
          createRemoteFolder(vcs, projectRoot.getUrl(), "tags", commitText);
        }
        return trunk;
    }
  }

  private static boolean isFolderEmpty(@NotNull SvnVcs vcs, @NotNull String folderUrl) throws VcsException {
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> SvnUtil.remoteFolderIsEmpty(vcs, folderUrl), "Check Remote Folder Contents", false, vcs.getProject());
  }

  @NotNull
  private static Target createRemoteFolder(@NotNull SvnVcs vcs,
                                           @NotNull Url parent,
                                           @NotNull String folderName,
                                           @NotNull String commitText) throws VcsException {
    Url url = append(parent, folderName);
    String message =
      message("share.directory.commit.message", folderName, ApplicationNamesInfo.getInstance().getFullProductName(), commitText);
    Target target = Target.on(url);

    progress(message("share.directory.create.dir.progress.text", url.toDecodedString()));

    long revision = vcs.getFactoryFromSettings().createBrowseClient().createDirectory(target, message, false);
    return Target.on(url, Revision.of(revision));
  }

  @Override
  protected void doVcsRefresh(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    VcsDirtyScopeManager.getInstance(vcs.getProject()).dirDirtyRecursively(file);
  }

  private static void addRecursively(@NotNull SvnVcs vcs, @NotNull ClientFactory factory, @NotNull VirtualFile rootFile)
    throws VcsException {
    SvnExcludingIgnoredOperation operation = new SvnExcludingIgnoredOperation(vcs.getProject(), file -> {
      progress(message("share.or.import.add.progress.text", file.getPath()));

      factory.createAddClient().add(virtualToIoFile(file), Depth.EMPTY, false, false, true, null);
    }, Depth.INFINITY);

    operation.execute(rootFile);
  }

  @Override
  protected void batchPerform(@NotNull SvnVcs vcs, @NotNull VirtualFile[] files, @NotNull DataContext context) {
  }

  protected boolean isBatchAction() {
    return false;
  }
}
