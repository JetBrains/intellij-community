package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeImpl;
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.update.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnChangeProvider;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.update.RefreshVFsSynchronously;
import org.jetbrains.idea.svn.update.SvnStatusWorker;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.jetbrains.idea.svn.update.UpdateFilesHelper;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

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

  private boolean myStoppedOnError;

  private final List<VcsException> myExceptions;

  private final UpdateEventHandler myHandler;
  private final Merger myMerger;
  private final ResolveWorker myResolveWorker;

  public SvnIntegrateChangesTask(final Project project, final SvnVcs vcs,
                                 final WorkingCopyInfo info, final MergerFactory mergerFactory,
                                 final SVNURL currentBranchUrl) {
    super(project, SvnBundle.message("action.Subversion.integrate.changes.messages.title"), true,
          VcsConfiguration.getInstance(project).getUpdateOption());

    myProjectLevelVcsManager = ProjectLevelVcsManagerEx.getInstanceEx(myProject);
    myVcs = vcs;

    myInfo = info;

    myAccomulatedFiles = new UpdatedFilesReverseSide(UpdatedFiles.create());
    myExceptions = new ArrayList<VcsException>();

    myHandler = new IntegrateEventHandler(myVcs, ProgressManager.getInstance().getProgressIndicator());
    myMerger = mergerFactory.createMerger(myVcs, new File(info.getLocalPath()), SvnConfiguration.getInstance(project).MERGE_DRY_RUN,
                                          myHandler, currentBranchUrl);
    myResolveWorker = new ResolveWorker(myInfo.isUnderProjectRoot(), myProject);
  }

  public void run(@NotNull final ProgressIndicator indicator) {
    BlockReloadingUtil.block();
    myProjectLevelVcsManager.startBackgroundVcsOperation();

    myRecentlyUpdatedFiles = UpdatedFiles.create();
    myHandler.setUpdatedFiles(myRecentlyUpdatedFiles);

    try {
      if (indicator.isCanceled()) {
        return;
      }
      myMerger.mergeNext();
    } catch (SVNException e) {
      myExceptions.add(new VcsException(e));
    }

    RefreshVFsSynchronously.updateAllChanged(myProject, myRecentlyUpdatedFiles);
  }

  public void onCancel() {
    onSuccess();
  }

  public void onSuccess() {
    try {
      afterExecution();
    } finally {
      myProjectLevelVcsManager.stopBackgroundVcsOperation();
    }
  }

  private Runnable createAfterExecutionRunnable() {
    return new AfterExecution();
  }

  private void afterExecution() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        createAfterExecutionRunnable().run();
        BlockReloadingUtil.unblock();
      }
    }
    );
  }

  public boolean hasNext() {
    return myMerger.hasNext();
  }

  private class AfterExecution implements Runnable {
    public void run() {
      final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null) {
        indicator.setText(VcsBundle.message("progress.text.updating.done"));
      }

      if (! myRecentlyUpdatedFiles.isEmpty()) {
        myResolveWorker.execute(myRecentlyUpdatedFiles);
      }

      myAccomulatedFiles.accomulateFiles(myRecentlyUpdatedFiles, UpdatedFilesReverseSide.DuplicateLevel.DUPLICATE_ERRORS);

      myStoppedOnError = (! myExceptions.isEmpty()) || myAccomulatedFiles.containErrors();

      if ((! myMerger.hasNext()) || myStoppedOnError) {
        if (myAccomulatedFiles.isEmpty() && myExceptions.isEmpty()) {
          Messages.showMessageDialog(SvnBundle.message("action.Subversion.integrate.changes.message.files.up.to.date.text"),
                                     SvnBundle.message("action.Subversion.integrate.changes.messages.title"),
                                     Messages.getInformationIcon());
        } else {
          finishActions();
        }
      } else {
        stepToNextChangeList();
      }
    }

    private void finishActions() {
      if (! myStoppedOnError) {
        if (myAccomulatedFiles.isEmpty()) {
          return;
        }

        if (myInfo.isUnderProjectRoot()) {
          showLocalCommit();
        } else {
          showAlienCommit();
        }
      }

      if (! myInfo.isUnderProjectRoot()) {
        finishAfterCommit();
      }
    }

    private void finishAfterCommit() {
      if (SvnConfiguration.getInstance(myVcs.getProject()).UPDATE_RUN_STATUS) {
        final UpdatedFiles statusFiles = doStatus();
        myAccomulatedFiles.accomulateFiles(statusFiles, UpdatedFilesReverseSide.DuplicateLevel.DUPLICATE_ERRORS_LOCALS);
      }

      if (myStoppedOnError) {
        myMerger.addWarnings(new WarningsAdder());
      }

      if (! myExceptions.isEmpty()) {
        AbstractVcsHelper.getInstance(myProject).showErrors(myExceptions, VcsBundle.message("message.title.vcs.update.errors"));
      }

      if (! myAccomulatedFiles.isEmpty()) {
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

    private void showLocalCommit() {
      final Collection<FilePath> files = new ArrayList<FilePath>();
      UpdateFilesHelper.iterateFileGroupFiles(myAccomulatedFiles.getUpdatedFiles(), new UpdateFilesHelper.Callback() {
        public void onFile(final String filePath, final String groupId) {
          final FilePath file = FilePathImpl.create(new File(filePath));
          files.add(file);
        }
      });

      ChangeListManager.getInstance(myProject).invokeAfterUpdate(new Runnable() {
        public void run() {
          CommitChangeListDialog.commitPaths(myProject, files, null, null);
          finishAfterCommit();
        }
      });
    }

    private void showAlienCommit() {
      final VcsDirtyScopeImpl dirtyScope = new VcsDirtyScopeImpl(myVcs, myProject);

      UpdateFilesHelper.iterateFileGroupFiles(myAccomulatedFiles.getUpdatedFiles(), new UpdateFilesHelper.Callback() {
        public void onFile(final String filePath, final String groupId) {
          final FilePath file = FilePathImpl.create(new File(filePath));
          dirtyScope.addDirtyFile(file);
        }
      });

      final SvnChangeProvider provider = new SvnChangeProvider(myVcs);
      final GatheringChangelistBuilder clb = new GatheringChangelistBuilder();
      try {
        provider.getChanges(dirtyScope, clb, null);
      } catch (VcsException e) {
        Messages.showErrorDialog(SvnBundle.message("action.Subversion.integrate.changes.error.unable.to.collect.changes.text",
            e.getMessage()), SvnBundle.message("action.Subversion.integrate.changes.alien.commit.changelist.title"));
        return;
      }

      if (! clb.getChanges().isEmpty()) {
        CommitChangeListDialog.commitAlienChanges(myProject, clb.getChanges(), myVcs,
                                                  SvnBundle.message("action.Subversion.integrate.changes.alien.commit.changelist.title"));
      }
    }
  }

  private class WarningsAdder implements WarningsHolder {
    public void addWarning(final String s) {
      final VcsException result = new VcsException(s);
      result.setIsWarning(true);
      myExceptions.add(result);
    }
  }
}
