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
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.update.*;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.update.SvnStatusWorker;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class SvnIntegrateChangesTask extends Task.Backgroundable {
  private final ProjectLevelVcsManagerEx myProjectLevelVcsManager;
  private final SvnVcs myVcs;
  private final WorkingCopyInfo myInfo;

  private final UpdatedFilesReverseSide myAccomulatedFiles;
  private UpdatedFiles myRecentlyUpdatedFiles;

  private final List<VcsException> myExceptions;

  private UpdateEventHandler myHandler;
  private IMerger myMerger;
  private ResolveWorker myResolveWorker;
  private FilePathImpl myMergeTarget;
  private final String myTitle;
  private final String myBranchName;
  private final MergerFactory myMergerFactory;
  private final SVNURL myCurrentBranchUrl;
  private boolean myDryRun;

  public SvnIntegrateChangesTask(final SvnVcs vcs, final WorkingCopyInfo info, final MergerFactory mergerFactory,
                                 final SVNURL currentBranchUrl, final String title, final boolean dryRun, String branchName) {
    super(vcs.getProject(), title, true, VcsConfiguration.getInstance(vcs.getProject()).getUpdateOption());
    myMergerFactory = mergerFactory;
    myCurrentBranchUrl = currentBranchUrl;
    myDryRun = dryRun;
    myTitle = title;
    myBranchName = branchName;

    myProjectLevelVcsManager = ProjectLevelVcsManagerEx.getInstanceEx(myProject);
    myVcs = vcs;

    myInfo = info;

    myAccomulatedFiles = new UpdatedFilesReverseSide(UpdatedFiles.create());
    myExceptions = new ArrayList<VcsException>();
    myHandler = new IntegrateEventHandler(myVcs, ProgressManager.getInstance().getProgressIndicator());
    myMerger = myMergerFactory.createMerger(myVcs, new File(myInfo.getLocalPath()), myHandler, myCurrentBranchUrl, myBranchName);
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
    myHandler.setProgressIndicator(ProgressManager.getInstance().getProgressIndicator());
    myResolveWorker = new ResolveWorker(myInfo.isUnderProjectRoot(), myProject);

    BlockReloadingUtil.block();
    myProjectLevelVcsManager.startBackgroundVcsOperation();

    try {
      myRecentlyUpdatedFiles = UpdatedFiles.create();
      myHandler.setUpdatedFiles(myRecentlyUpdatedFiles);

      indicatorOnStart();

      // try to do multiple under single progress
      while (true) {
        doMerge();

        RefreshVFsSynchronously.updateAllChanged(myRecentlyUpdatedFiles);
        indicator.setText(VcsBundle.message("progress.text.updating.done"));

        if (myResolveWorker.needsInteraction(myRecentlyUpdatedFiles) || (! myMerger.hasNext()) ||
            (! myExceptions.isEmpty()) || UpdatedFilesReverseSide.containErrors(myRecentlyUpdatedFiles)) {
          break;
        }
        accomulate();
      }
    } finally {
      myProjectLevelVcsManager.stopBackgroundVcsOperation();
    }
  }

  private void createMessage(final boolean getLatest, final boolean warning, final String firstString) {
    final List<String> messages = new ArrayList<String>();
    messages.add(firstString);
    myMerger.getInfo(new Consumer<String>() {
      public void consume(final String s) {
        messages.add(s);
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
    try {
      if (myProject.isDisposed()) return;
      afterExecution(true);
    } finally {
      BlockReloadingUtil.unblock();
    }
  }

  public void onSuccess() {
    try {
      if (myProject.isDisposed()) return;
      afterExecution(false);
    } finally {
      BlockReloadingUtil.unblock();
    }
  }

  private void accomulate() {
    myAccomulatedFiles.accomulateFiles(myRecentlyUpdatedFiles, UpdatedFilesReverseSide.DuplicateLevel.DUPLICATE_ERRORS);
  }

  private void afterExecution(final boolean wasCanceled) {
    if (! myRecentlyUpdatedFiles.isEmpty()) {
      myResolveWorker.execute(myRecentlyUpdatedFiles);
    }
    final boolean haveConflicts = ResolveWorker.haveUnresolvedConflicts(myRecentlyUpdatedFiles);

    accomulate();

    if ((! myMerger.hasNext()) || haveConflicts || (! myExceptions.isEmpty()) || myAccomulatedFiles.containErrors() || wasCanceled) {
      initMergeTarget();
      if (myAccomulatedFiles.isEmpty() && myExceptions.isEmpty() && (myMergeTarget == null) && (! wasCanceled)) {
        Messages.showMessageDialog(SvnBundle.message("action.Subversion.integrate.changes.message.files.up.to.date.text"), myTitle,
                                   Messages.getInformationIcon());
      } else {
        if (haveConflicts) {
          final VcsException exception = new VcsException(SvnBundle.message("svn.integrate.changelist.warning.unresolved.conflicts.text"));
          exception.setIsWarning(true);
          myExceptions.add(exception);
        }
        if (wasCanceled) {
          final List<String> details = new LinkedList<String>();
          details.add("Integration was canceled");
          myMerger.getSkipped(new Consumer<String>() {
            public void consume(String s) {
              if (! StringUtil.isEmptyOrSpaces(s)) {
                details.add(s);
              }
            }
          });
          final VcsException exception = new VcsException(details);
          exception.setIsWarning(true);
          myExceptions.add(exception);
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
      if ((! myDryRun) && (myExceptions.isEmpty()) && (! myAccomulatedFiles.containErrors()) &&
          ((! myAccomulatedFiles.isEmpty()) || (myMergeTarget != null))) {
        if (myInfo.isUnderProjectRoot()) {
          showLocalCommit();
        } else {
          showAlienCommit();
        }
        return;
      }
    }

    prepareAndShowResults();
  }

  // no remote operations
  private void prepareAndShowResults() {
    // todo unite into one window??
    if (! myAccomulatedFiles.isEmpty()) {
      if (SvnConfiguration.getInstance(myVcs.getProject()).UPDATE_RUN_STATUS) {
        doStatus(new Consumer<UpdatedFiles>() {
          public void consume(final UpdatedFiles updatedFiles) {
            myAccomulatedFiles.accomulateFiles(updatedFiles, UpdatedFilesReverseSide.DuplicateLevel.DUPLICATE_ERRORS_LOCALS);
            showUpdateTree();
          }
        });
      } else {
        showUpdateTree();
      }
    }
    if (! myExceptions.isEmpty()) {
      AbstractVcsHelper.getInstance(myProject).showErrors(myExceptions, VcsBundle.message("message.title.vcs.update.errors", myExceptions.size()));
    }
  }

  private void showUpdateTree() {
    RestoreUpdateTree restoreUpdateTree = RestoreUpdateTree.getInstance(myProject);
    // action info is actually NOT used
    restoreUpdateTree.registerUpdateInformation(myAccomulatedFiles.getUpdatedFiles(), ActionInfo.INTEGRATE);
    myProjectLevelVcsManager.showUpdateProjectInfo(myAccomulatedFiles.getUpdatedFiles(), myTitle, ActionInfo.INTEGRATE);
  }

  private void doStatus(final Consumer<UpdatedFiles> afterStatus) {
    final UpdatedFiles statusFiles = UpdatedFiles.create();

    ProgressManager.getInstance().run(new Backgroundable(myProject, SvnBundle.message("retrieving.subversion.status.text"), true, PerformInBackgroundOption.DEAF) {
      public void run(@NotNull final ProgressIndicator indicator) {
        final SvnStatusWorker statusWorker = new SvnStatusWorker(myVcs, new ArrayList<File>(), new File(myInfo.getLocalPath()),
                                                                 statusFiles, false, myExceptions);
        statusWorker.doStatus();
      }

      @Override
      public void onCancel() {
        onSuccess();
      }

      @Override
      public void onSuccess() {
        afterStatus.consume(statusFiles);
      }
    });
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

        myMergeTarget = FilePathImpl.create(mergeInfoHolder, mergeInfoHolder.isDirectory());
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
    final ChangeListManager clManager = ChangeListManager.getInstance(myProject);
    clManager.invokeAfterUpdate(new Runnable() {
      public void run() {
        final Collection<Change> changes = new ArrayList<Change>();
        for (FilePath file : files) {
          final Change change = clManager.getChange(file);
          if (change != null) {
            changes.add(change);
          }
        }
        CommitChangeListDialog.commitChanges(myProject, changes, null, null, myMerger.getComment());
        prepareAndShowResults();
      }
    }, InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE, myTitle,
      new Consumer<VcsDirtyScopeManager>() {
        public void consume(final VcsDirtyScopeManager vcsDirtyScopeManager) {
          vcsDirtyScopeManager.filePathsDirty(files, null);
        }
      }, null);
  }

  private void showAlienCommit() {
    final AlienDirtyScope dirtyScope = new AlienDirtyScope();

    if (myMergeTarget != null) {
      dirtyScope.addDir(myMergeTarget);
    } else {
      UpdateFilesHelper.iterateFileGroupFiles(myAccomulatedFiles.getUpdatedFiles(), new UpdateFilesHelper.Callback() {
        public void onFile(final String filePath, final String groupId) {
          final FilePath file = FilePathImpl.create(new File(filePath));
          dirtyScope.addFile(file);
        }
      });
    }

    final SvnChangeProvider provider = new SvnChangeProvider(myVcs);
    final GatheringChangelistBuilder clb = new GatheringChangelistBuilder(myProject, myAccomulatedFiles, myMergeTarget == null ? null : myMergeTarget.getVirtualFile());
    try {
      provider.getChanges(dirtyScope, clb, ProgressManager.getInstance().getProgressIndicator(), null);
    } catch (VcsException e) {
      Messages.showErrorDialog(SvnBundle.message("action.Subversion.integrate.changes.error.unable.to.collect.changes.text",
                                                 e.getMessage()), myTitle);
      return;
    }

    if (! clb.getChanges().isEmpty()) {
      CommitChangeListDialog.commitAlienChanges(myProject, clb.getChanges(), myVcs, myMerger.getComment(), myMerger.getComment());
    }
  }
}
