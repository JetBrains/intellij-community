package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeImpl;
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.update.*;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.update.RefreshVFsSynchronously;
import org.jetbrains.idea.svn.update.SvnStatusWorker;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SvnIntegrateChangesTask extends Task.Backgroundable {
  private final ProjectLevelVcsManagerEx myProjectLevelVcsManager;
  private final SvnVcs myVcs;
  private final WorkingCopyInfo myInfo;

  private final UpdatedFilesReverseSide myAccomulatedFiles;
  private UpdatedFiles myRecentlyUpdatedFiles;

  private final List<VcsException> myExceptions;

  private final UpdateEventHandler myHandler;
  private final Merger myMerger;
  private final ResolveWorker myResolveWorker;
  private FilePathImpl myMergeTarget;

  public SvnIntegrateChangesTask(final SvnVcs vcs, final WorkingCopyInfo info, final MergerFactory mergerFactory,
                                 final SVNURL currentBranchUrl) {
    super(vcs.getProject(), SvnBundle.message("action.Subversion.integrate.changes.messages.title"), true,
          VcsConfiguration.getInstance(vcs.getProject()).getUpdateOption());

    myProjectLevelVcsManager = ProjectLevelVcsManagerEx.getInstanceEx(myProject);
    myVcs = vcs;

    myInfo = info;

    myAccomulatedFiles = new UpdatedFilesReverseSide(UpdatedFiles.create());
    myExceptions = new ArrayList<VcsException>();

    myHandler = new IntegrateEventHandler(myVcs, ProgressManager.getInstance().getProgressIndicator());
    myMerger = mergerFactory.createMerger(myVcs, new File(info.getLocalPath()), myHandler, currentBranchUrl);
    myResolveWorker = new ResolveWorker(myInfo.isUnderProjectRoot(), myProject);
  }

  private void indicatorOnStart() {
    final ProgressIndicator ind = ProgressManager.getInstance().getProgressIndicator();
    if (ind != null) {
      ind.setIndeterminate(true);
    }
    if (ind != null) {
      ind.setText(SvnBundle.message("action.Subversion.integrate.changes.progress.integrating.text"));
    }
  }

  public void run(@NotNull final ProgressIndicator indicator) {
    BlockReloadingUtil.block();
    myProjectLevelVcsManager.startBackgroundVcsOperation();

    myRecentlyUpdatedFiles = UpdatedFiles.create();
    myHandler.setUpdatedFiles(myRecentlyUpdatedFiles);

    indicatorOnStart();

    // try to do multiple under single progress
    while (true) {
      if (indicator.isCanceled()) {
        createMessage(false, true, SvnBundle.message("action.Subversion.integrate.changes.message.canceled.text"));
        return;
      }

      doMerge();

      RefreshVFsSynchronously.updateAllChanged(myRecentlyUpdatedFiles);
      indicator.setText(VcsBundle.message("progress.text.updating.done"));

      if (myResolveWorker.needsInteraction(myRecentlyUpdatedFiles) || (! myMerger.hasNext()) ||
          (! myExceptions.isEmpty()) || UpdatedFilesReverseSide.containErrors(myRecentlyUpdatedFiles)) {
        break;
      }
      accomulate();
    }
  }

  private void createMessage(final boolean getLatest, final boolean warning, final String firstString) {
    final List<String> messages = new ArrayList<String>();
    messages.add(firstString);
    myMerger.getInfo(new NotNullFunction<String, Boolean>() {
      @NotNull
      public Boolean fun(final String s) {
        messages.add(s);
        return Boolean.TRUE;
      }
    }, getLatest);
    final VcsException result = new VcsException(messages);
    result.setIsWarning(warning);
    myExceptions.add(result);
  }

  private void doMerge() {
    try {
      myMerger.mergeNext();
    } catch (SVNException e) {
      createMessage(true, false, e.getMessage());
    }
  }

  public void onCancel() {
    onSuccess();
  }

  public void onSuccess() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        try {
          afterExecution();
        } finally {
          BlockReloadingUtil.unblock();
          myProjectLevelVcsManager.stopBackgroundVcsOperation();
        }
      }
    }
    );
  }

  private void accomulate() {
    myAccomulatedFiles.accomulateFiles(myRecentlyUpdatedFiles, UpdatedFilesReverseSide.DuplicateLevel.DUPLICATE_ERRORS);
  }

  private void afterExecution() {
    if (! myRecentlyUpdatedFiles.isEmpty()) {
      myResolveWorker.execute(myRecentlyUpdatedFiles);
    }
    accomulate();

    if ((! myMerger.hasNext()) || (! myExceptions.isEmpty()) || myAccomulatedFiles.containErrors()) {
      initMergeTarget();
      if (myAccomulatedFiles.isEmpty() && myExceptions.isEmpty() && (myMergeTarget == null)) {
        Messages.showMessageDialog(SvnBundle.message("action.Subversion.integrate.changes.message.files.up.to.date.text"),
                                   SvnBundle.message("action.Subversion.integrate.changes.messages.title"),
                                   Messages.getInformationIcon());
      } else {
        finishActions();
      }
      myMerger.afterProcessing();
    } else {
      stepToNextChangeList();
    }
  }

  private void finishActions() {
    if ((! SvnConfiguration.getInstance(myProject).MERGE_DRY_RUN) && (myExceptions.isEmpty()) && (! myAccomulatedFiles.containErrors()) &&
        ((! myAccomulatedFiles.isEmpty()) || (myMergeTarget != null))) {
      if (myInfo.isUnderProjectRoot()) {
        showLocalCommit();
      } else {
        showAlienCommit();
      }
    }

    if ((! myInfo.isUnderProjectRoot()) || (myAccomulatedFiles.isEmpty())) {
      prepareAndShowResults();
    }
  }

  // no remote operations
  private void prepareAndShowResults() {
    if (! myExceptions.isEmpty()) {
      AbstractVcsHelper.getInstance(myProject).showErrors(myExceptions, VcsBundle.message("message.title.vcs.update.errors"));
    } else if (! myAccomulatedFiles.isEmpty()) {
      if (SvnConfiguration.getInstance(myVcs.getProject()).UPDATE_RUN_STATUS) {
        final UpdatedFiles statusFiles = doStatus();
        myAccomulatedFiles.accomulateFiles(statusFiles, UpdatedFilesReverseSide.DuplicateLevel.DUPLICATE_ERRORS_LOCALS);
      }
      showUpdateTree();
    }
  }

  private void showUpdateTree() {
    RestoreUpdateTree restoreUpdateTree = RestoreUpdateTree.getInstance(myProject);
    // action info is actually NOT used
    restoreUpdateTree.registerUpdateInformation(myAccomulatedFiles.getUpdatedFiles(), ActionInfo.INTEGRATE);
    myProjectLevelVcsManager.showUpdateProjectInfo(myAccomulatedFiles.getUpdatedFiles(),
                                                   SvnBundle.message("action.Subversion.integrate.changes.messages.title"), ActionInfo.INTEGRATE);
  }

  private UpdatedFiles doStatus() {
    final UpdatedFiles statusFiles = UpdatedFiles.create();
    final SvnStatusWorker statusWorker = new SvnStatusWorker(myVcs, new ArrayList<File>(), new File(myInfo.getLocalPath()),
                                                             statusFiles, false, myExceptions);
    statusWorker.doStatus();
    return statusFiles;
  }

  private void stepToNextChangeList() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        ProgressManager.getInstance().run(SvnIntegrateChangesTask.this);
      }
    });
  }

  /**
   * folder that is going to keep merge info record should also be changed
   */
  @Nullable
  private void initMergeTarget() {
    final File mergeInfoHolder = myMerger.getMergeInfoHolder();
    if (mergeInfoHolder != null) {
      final SVNStatus svnStatus = SvnUtil.getStatus(myVcs, mergeInfoHolder);
      if ((svnStatus != null) && (SVNStatusType.STATUS_MODIFIED.equals(svnStatus.getPropertiesStatus()))) {
        myMergeTarget = FilePathImpl.create(mergeInfoHolder);
      }
    }
  }

  private void showLocalCommit() {
    final Collection<FilePath> files = new ArrayList<FilePath>();
    UpdateFilesHelper.iterateFileGroupFiles(myAccomulatedFiles.getUpdatedFiles(), new UpdateFilesHelper.Callback() {
      public void onFile(final String filePath, final String groupId) {
        final FilePath file = FilePathImpl.create(new File(filePath));
        files.add(file);
      }
    });
    if (myMergeTarget != null) {
      files.add(myMergeTarget);
    }

    // for changes to be detected, we need switch to background change list manager update thread and back to dispatch thread
    // so callback is used; ok to be called after VCS update markup closed: no remote operations
    ChangeListManager.getInstance(myProject).invokeAfterUpdate(new Runnable() {
      public void run() {
        CommitChangeListDialog.commitPaths(myProject, files, null, null, myMerger.getComment());
        prepareAndShowResults();
      }
    }, true, false, SvnBundle.message("action.Subversion.integrate.changes.messages.title"));
  }

  private void showAlienCommit() {
    final VcsDirtyScopeImpl dirtyScope = new VcsDirtyScopeImpl(myVcs, myProject);

    UpdateFilesHelper.iterateFileGroupFiles(myAccomulatedFiles.getUpdatedFiles(), new UpdateFilesHelper.Callback() {
      public void onFile(final String filePath, final String groupId) {
        final FilePath file = FilePathImpl.create(new File(filePath));
        dirtyScope.addDirtyFile(file);
      }
    });
    if (myMergeTarget != null) {
      dirtyScope.addDirtyFile(myMergeTarget);
    }

    final SvnChangeProvider provider = new SvnChangeProvider(myVcs);
    final GatheringChangelistBuilder clb = new GatheringChangelistBuilder();
    try {
      provider.getChanges(dirtyScope, clb, ProgressManager.getInstance().getProgressIndicator());
    } catch (VcsException e) {
      Messages.showErrorDialog(SvnBundle.message("action.Subversion.integrate.changes.error.unable.to.collect.changes.text",
                                                 e.getMessage()), SvnBundle.message("action.Subversion.integrate.changes.alien.commit.changelist.title"));
      return;
    }

    if (! clb.getChanges().isEmpty()) {
      CommitChangeListDialog.commitAlienChanges(myProject, clb.getChanges(), myVcs,
              SvnBundle.message("action.Subversion.integrate.changes.alien.commit.changelist.title"), myMerger.getComment());
    }
  }
}
