// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.integrate;

import com.intellij.configurationStore.StoreReloadManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts.ProgressTitle;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vcs.update.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.ViewUpdateInfoNotification;
import com.intellij.vcs.commit.SingleChangeListCommitWorkflowHandler;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnChangeProvider;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;
import org.jetbrains.idea.svn.update.UpdateEventHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.vcs.update.ActionInfo.INTEGRATE;
import static org.jetbrains.idea.svn.SvnBundle.message;

public class SvnIntegrateChangesTask extends Task.Backgroundable {
  private final ProjectLevelVcsManagerEx myProjectLevelVcsManager;
  private final SvnVcs myVcs;
  private final WorkingCopyInfo myInfo;

  private final UpdatedFilesReverseSide myAccumulatedFiles;
  private UpdatedFiles myRecentlyUpdatedFiles;

  private final List<VcsException> myExceptions;

  private final UpdateEventHandler myHandler;
  private final IMerger myMerger;
  private ResolveWorker myResolveWorker;
  private FilePath myMergeTarget;
  private final boolean myDryRun;

  public SvnIntegrateChangesTask(
    final SvnVcs vcs,
    @NotNull WorkingCopyInfo info,
    final MergerFactory mergerFactory,
    final Url currentBranchUrl,
    @ProgressTitle @NotNull String title,
    final boolean dryRun,
    String branchName
  ) {
    super(vcs.getProject(), title, true);
    myDryRun = dryRun;

    myProjectLevelVcsManager = ProjectLevelVcsManagerEx.getInstanceEx(myProject);
    myVcs = vcs;

    myInfo = info;

    myAccumulatedFiles = new UpdatedFilesReverseSide(UpdatedFiles.create());
    myExceptions = new ArrayList<>();
    myHandler = new IntegrateEventHandler(myVcs, ProgressManager.getInstance().getProgressIndicator());
    myMerger = mergerFactory.createMerger(myVcs, new File(myInfo.getLocalPath()), myHandler, currentBranchUrl, branchName);
  }

  private static void indicatorOnStart() {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    if (indicator != null) {
      indicator.setIndeterminate(true);
      indicator.setText(message("action.Subversion.integrate.changes.progress.integrating.text"));
    }
  }

  @Override
  public void run(final @NotNull ProgressIndicator indicator) {
    myHandler.setProgressIndicator(ProgressManager.getInstance().getProgressIndicator());
    myResolveWorker = new ResolveWorker(myInfo.isUnderProjectRoot(), myProject);

    if (myProject != null) {
      StoreReloadManager.Companion.getInstance(myProject).blockReloadingProjectOnExternalChanges();
    }
    myProjectLevelVcsManager.startBackgroundVcsOperation();

    try {
      myRecentlyUpdatedFiles = UpdatedFiles.create();
      myHandler.setUpdatedFiles(myRecentlyUpdatedFiles);

      indicatorOnStart();

      // try to do multiple under single progress
      while (true) {
        doMerge();

        RefreshVFsSynchronously.INSTANCE.updateAllChanged(myRecentlyUpdatedFiles);
        indicator.setText(VcsBundle.message("progress.text.updating.done"));

        if (myResolveWorker.needsInteraction(myRecentlyUpdatedFiles) || (! myMerger.hasNext()) ||
            (! myExceptions.isEmpty()) || UpdatedFilesReverseSide.containErrors(myRecentlyUpdatedFiles)) {
          break;
        }
        accumulate();
      }
    } finally {
      myProjectLevelVcsManager.stopBackgroundVcsOperation();
    }
  }

  private static @NotNull VcsException createException(boolean isWarning, @Nls @Nullable String @NotNull ... messages) {
    Collection<String> notEmptyMessages = ContainerUtil.mapNotNull(messages, message -> StringUtil.nullize(message, true));

    return new VcsException(notEmptyMessages).setIsWarning(isWarning);
  }

  private void doMerge() {
    myHandler.startUpdate();
    try {
      myMerger.mergeNext();
    }
    catch (VcsException e) {
      myExceptions.add(createException(false, e.getMessage(), myMerger.getInfo(), myMerger.getSkipped()));
    }
    finally {
      myHandler.finishUpdate();
    }
  }

  @Override
  public void onCancel() {
    onTaskFinished(true);
  }

  @Override
  public void onSuccess() {
    onTaskFinished(false);
  }

  private void onTaskFinished(boolean wasCancelled) {
    TransactionGuard.submitTransaction(getProject(), () -> {
      try {
        afterExecution(wasCancelled);
      }
      finally {
        StoreReloadManager.Companion.getInstance(myProject).unblockReloadingProjectOnExternalChanges();
      }
    });
  }

  private void accumulate() {
    myAccumulatedFiles.accumulateFiles(myRecentlyUpdatedFiles, UpdatedFilesReverseSide.DuplicateLevel.DUPLICATE_ERRORS);
  }

  private void afterExecution(final boolean wasCanceled) {
    if (! myRecentlyUpdatedFiles.isEmpty()) {
      myResolveWorker.execute(myRecentlyUpdatedFiles);
    }
    final boolean haveConflicts = ResolveWorker.haveUnresolvedConflicts(myRecentlyUpdatedFiles);

    accumulate();

    if ((!myMerger.hasNext()) || haveConflicts || (!myExceptions.isEmpty()) || myAccumulatedFiles.containErrors() || wasCanceled) {
      initMergeTarget();
      if (myAccumulatedFiles.isEmpty() && myExceptions.isEmpty() && (myMergeTarget == null) && (!wasCanceled)) {
        Messages.showMessageDialog(message("action.Subversion.integrate.changes.message.files.up.to.date.text"), getTitle(),
                                   Messages.getInformationIcon());
      } else {
        if (haveConflicts) {
          myExceptions.add(createException(true, message("svn.integrate.changelist.warning.unresolved.conflicts.text")));
        }
        if (wasCanceled) {
          myExceptions.add(createException(true, message("error.integration.was.canceled"), myMerger.getSkipped()));
        }
        finishActions(wasCanceled);
      }
      myMerger.afterProcessing();
    } else {
      stepToNextChangeList();
    }
  }

  private void finishActions(final boolean wasCanceled) {
    if (! wasCanceled) {
      if (! ApplicationManager.getApplication().isUnitTestMode() &&
          (! myDryRun) && (myExceptions.isEmpty()) && (! myAccumulatedFiles.containErrors()) &&
          ((! myAccumulatedFiles.isEmpty()) || (myMergeTarget != null))) {
        if (myInfo.isUnderProjectRoot()) {
          showLocalCommit();
        } else {
          showAlienCommit();
        }
        return;
      }
    }

    final Collection<FilePath> files = gatherChangedPaths();
    VcsDirtyScopeManager.getInstance(getProject()).filePathsDirty(files, null);
    prepareAndShowResults();
  }

  // no remote operations
  private void prepareAndShowResults() {
    // todo unite into one window??
    if (!myAccumulatedFiles.isEmpty()) {
      showUpdateTree();
    }
    if (! myExceptions.isEmpty()) {
      AbstractVcsHelper.getInstance(myProject).showErrors(myExceptions, VcsBundle.message("message.title.vcs.update.errors", myExceptions.size()));
    }
  }

  private void showUpdateTree() {
    RestoreUpdateTree restoreUpdateTree = RestoreUpdateTree.getInstance(getProject());
    // action info is actually NOT used
    restoreUpdateTree.registerUpdateInformation(myAccumulatedFiles.getUpdatedFiles(), INTEGRATE);
    UpdateInfoTree tree =
      myProjectLevelVcsManager.showUpdateProjectInfo(myAccumulatedFiles.getUpdatedFiles(), getTitle(), INTEGRATE, false);
    if (tree != null) ViewUpdateInfoNotification.focusUpdateInfoTree(getProject(), tree);
  }

  private void stepToNextChangeList() {
    ApplicationManager.getApplication().invokeLater(() -> ProgressManager.getInstance().run(this));
  }

  /**
   * folder that is going to keep merge info record should also be changed
   */
  private void initMergeTarget() {
    final File mergeInfoHolder = myMerger.getMergeInfoHolder();
    if (mergeInfoHolder != null) {
      final Status svnStatus = SvnUtil.getStatus(myVcs, mergeInfoHolder);
      if (svnStatus != null && svnStatus.isProperty(StatusType.STATUS_MODIFIED)) {
        myMergeTarget = VcsUtil.getFilePath(mergeInfoHolder);
      }
    }
  }

  private void showLocalCommit() {
    final Collection<FilePath> files = gatherChangedPaths();

    // for changes to be detected, we need switch to background change list manager update thread and back to dispatch thread
    // so callback is used; ok to be called after VCS update markup closed: no remote operations
    VcsDirtyScopeManager.getInstance(getProject()).filePathsDirty(files, null);
    final ChangeListManager changeListManager = ChangeListManager.getInstance(getProject());
    changeListManager.invokeAfterUpdateWithModal(true, getTitle(), () -> {
      Collection<Change> changes = new ArrayList<>();
      for (FilePath file : files) {
        ContainerUtil.addIfNotNull(changes, changeListManager.getChange(file));
      }

      CommitChangeListDialog.commitVcsChanges(getProject(), changes, null, myMerger.getComment(), null);
      prepareAndShowResults();
    });
  }

  private @NotNull Collection<FilePath> gatherChangedPaths() {
    final Collection<FilePath> result = new ArrayList<>();

    UpdateFilesHelper.iterateFileGroupFiles(myAccumulatedFiles.getUpdatedFiles(),
                                            (filePath, groupId) -> result.add(VcsUtil.getFilePath(new File(filePath))));
    ContainerUtil.addIfNotNull(result, myMergeTarget);

    return result;
  }

  private void showAlienCommit() {
    final AlienDirtyScope dirtyScope = new AlienDirtyScope(myVcs);

    if (myMergeTarget != null) {
      dirtyScope.addDir(myMergeTarget);
    } else {
      UpdateFilesHelper.iterateFileGroupFiles(myAccumulatedFiles.getUpdatedFiles(),
                                              (filePath, groupId) -> dirtyScope.addFile(VcsUtil.getFilePath(new File(filePath))));
    }

    showAlienCommit(dirtyScope);
  }

  private void showAlienCommit(final @NotNull AlienDirtyScope dirtyScope) {
    new Task.Backgroundable(myVcs.getProject(),
                            message("action.Subversion.integrate.changes.collecting.changes.to.commit.task.title")) {

      private final GatheringChangelistBuilder changesBuilder = new GatheringChangelistBuilder(myVcs, myAccumulatedFiles);
      private final Ref<@Nls String> caughtError = new Ref<>();

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);

        if (!myVcs.getProject().isDisposed()) {
          try {
            new SvnChangeProvider(myVcs).getChanges(dirtyScope, changesBuilder, indicator, new FakeGate());
          }
          catch (VcsException e) {
            caughtError.set(message("action.Subversion.integrate.changes.error.unable.to.collect.changes.text", e.getMessage()));
          }
        }
      }

      @Override
      public void onSuccess() {
        if (!caughtError.isNull()) {
          VcsBalloonProblemNotifier.showOverVersionControlView(myVcs.getProject(), caughtError.get(), MessageType.ERROR);
          return;
        }

        List<Change> changes = changesBuilder.getChanges();
        if (changes.isEmpty()) return;

        String comment = myMerger.getComment();
        AlienLocalChangeList changeList = new AlienLocalChangeList(changes, comment);

        AlienCommitWorkflow workflow = new AlienCommitWorkflow(myVcs);
        AlienCommitChangeListDialog dialog = new AlienCommitChangeListDialog(workflow, changeList);
        SingleChangeListCommitWorkflowHandler handler =
          new SingleChangeListCommitWorkflowHandler(workflow, dialog, comment, changes);
        handler.activate();
      }
    }.queue();
  }

  private static class FakeGate implements ChangeListManagerGate {
    @Override
    public @NotNull List<LocalChangeList> getListsCopy() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable LocalChangeList findChangeList(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull LocalChangeList addChangeList(@NotNull String name, String comment) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull LocalChangeList findOrCreateList(@NotNull String name, String comment) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void editComment(@NotNull String name, String comment) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void editName(@NotNull String oldName, @NotNull String newName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setListsToDisappear(@NotNull Collection<String> names) {
      throw new UnsupportedOperationException();
    }

    @Override
    public FileStatus getStatus(@NotNull VirtualFile file) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable FileStatus getStatus(@NotNull FilePath filePath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setDefaultChangeList(@NotNull String list) {
      throw new UnsupportedOperationException();
    }
  }
}
