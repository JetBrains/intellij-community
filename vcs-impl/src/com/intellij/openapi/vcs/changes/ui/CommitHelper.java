/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.MoveChangesToAnotherListAction;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ui.ConfirmationDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CommitHelper {
  private final Project myProject;

  private final ChangeList myChangeList;
  private final List<Change> myIncludedChanges;

  private final String myActionName;
  private final String myCommitMessage;

  private final List<CheckinHandler> myHandlers;
  private final boolean myAllOfDefaultChangeListChangesIncluded;
  private final boolean myForceSyncCommit;
  private final List<Document> myCommittingDocuments = new ArrayList<Document>();
  private final VcsConfiguration myConfiguration;
  private final VcsDirtyScopeManager myDirtyScopeManager;

  public CommitHelper(final Project project,
                      final ChangeList changeList,
                      final List<Change> includedChanges,
                      final String actionName,
                      final String commitMessage,
                      final List<CheckinHandler> handlers,
                      final boolean allOfDefaultChangeListChangesIncluded,
                      final boolean synchronously) {
    myProject = project;
    myChangeList = changeList;
    myIncludedChanges = includedChanges;
    myActionName = actionName;
    myCommitMessage = commitMessage;
    myHandlers = handlers;
    myAllOfDefaultChangeListChangesIncluded = allOfDefaultChangeListChangesIncluded;
    myForceSyncCommit = synchronously;
    myConfiguration = VcsConfiguration.getInstance(myProject);
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
  }

  public boolean doCommit() {
    return doCommit(new CommitProcessor());
  }

  public boolean doAlienCommit(final AbstractVcs vcs) {
    return doCommit(new AlienCommitProcessor(vcs));
  }

  private boolean doCommit(final GeneralCommitProcessor processor) {

    final Runnable action = new Runnable() {
      public void run() {
        generalCommit(processor);
      }
    };

    if (myForceSyncCommit) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(action, myActionName, true, myProject);
      return doesntContainErrors(processor.getVcsExceptions());
    }
    else {
      Task.Backgroundable task =
        new Task.Backgroundable(myProject, myActionName, true, myConfiguration.getCommitOption()) {
          public void run(@NotNull final ProgressIndicator indicator) {
            final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
            vcsManager.startBackgroundVcsOperation();
            try {
              action.run();
            }
            finally {
              vcsManager.stopBackgroundVcsOperation();
            }
          }

          @Nullable
          public NotificationInfo getNotificationInfo() {
            final List<Change> changesFailedToCommit = processor.getChangesFailedToCommit();
            
            String text = (myIncludedChanges.size() - changesFailedToCommit.size()) + " Change(s) Commited";
            if (changesFailedToCommit.size() > 0) {
              text += ", " + changesFailedToCommit.size() + " Change(s) Failed To Commit";
            }
            return new NotificationInfo("VCS Commit",  "VCS Commit Finished", text, true);
          }
        };
      ProgressManager.getInstance().run(task);
      return false;
    }
  }

  private static boolean doesntContainErrors(final List<VcsException> vcsExceptions) {
    for (VcsException vcsException : vcsExceptions) {
      if (!vcsException.isWarning()) return false;
    }
    return true;
  }

  private void generalCommit(final GeneralCommitProcessor processor) {
    try {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          markCommittingDocuments();
        }
      });

      processor.callSelf();

      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          unmarkCommittingDocuments();
        }
      });

      processor.doBeforeRefresh();
      VirtualFileManager.getInstance().refresh(true, processor.postRefresh());

      AbstractVcsHelper.getInstance(myProject).showErrors(processor.getVcsExceptions(), myActionName);
    }
    finally {
      commitCompleted(processor.getVcsExceptions(), processor);
    }
  }

  private class AlienCommitProcessor extends GeneralCommitProcessor {
    private final AbstractVcs myVcs;

    private AlienCommitProcessor(final AbstractVcs vcs) {
      myVcs = vcs;
    }

    public void callSelf() {
      ChangesUtil.processItemsByVcs(myIncludedChanges, new ChangesUtil.VcsSeparator<Change>() {
        public AbstractVcs getVcsFor(final Change item) {
          return myVcs;
        }
      }, this);
    }

    public void process(final AbstractVcs vcs, final List<Change> items) {
      if (myVcs.getName().equals(vcs.getName())) {
        final CheckinEnvironment environment = vcs.getCheckinEnvironment();
        if (environment != null) {
          Collection<FilePath> paths = ChangesUtil.getPaths(items);
          myPathsToRefresh.addAll(paths);

          final List<VcsException> exceptions = environment.commit(items, myCommitMessage);
          if (exceptions != null && exceptions.size() > 0) {
            myVcsExceptions.addAll(exceptions);
            myChangesFailedToCommit.addAll(items);
          }
        }
      }
    }

    public void afterSuccessfulCheckIn() {

    }

    public void afterFailedCheckIn() {
    }

    public void doBeforeRefresh() {
    }

    public Runnable postRefresh() {
      return null;
    }
  }

  private abstract static class GeneralCommitProcessor implements ChangesUtil.PerVcsProcessor<Change>, ActionsAroundRefresh {
    protected final List<FilePath> myPathsToRefresh;
    protected final List<VcsException> myVcsExceptions;
    protected final List<Change> myChangesFailedToCommit;

    protected GeneralCommitProcessor() {
      myPathsToRefresh = new ArrayList<FilePath>();
      myVcsExceptions = new ArrayList<VcsException>();
      myChangesFailedToCommit = new ArrayList<Change>();
    }

    public abstract void callSelf();
    public abstract void afterSuccessfulCheckIn();
    public abstract void afterFailedCheckIn();

    public List<FilePath> getPathsToRefresh() {
      return myPathsToRefresh;
    }

    public List<VcsException> getVcsExceptions() {
      return myVcsExceptions;
    }

    public List<Change> getChangesFailedToCommit() {
      return myChangesFailedToCommit;
    }
  }

  private interface ActionsAroundRefresh {
    void doBeforeRefresh();
    Runnable postRefresh();
  }

  private class CommitProcessor extends GeneralCommitProcessor {
    private boolean myKeepChangeListAfterCommit;
    private LocalHistoryAction myAction;

    public void callSelf() {
      ChangesUtil.processChangesByVcs(myProject, myIncludedChanges, this);
    }

    public void process(final AbstractVcs vcs, final List<Change> items) {
      final CheckinEnvironment environment = vcs.getCheckinEnvironment();
      if (environment != null) {
        Collection<FilePath> paths = ChangesUtil.getPaths(items);
        myPathsToRefresh.addAll(paths);
        if (environment.keepChangeListAfterCommit(myChangeList)) {
          myKeepChangeListAfterCommit = true;
        }
        final List<VcsException> exceptions = environment.commit(items, myCommitMessage);
        if (exceptions != null && exceptions.size() > 0) {
          myVcsExceptions.addAll(exceptions);
          myChangesFailedToCommit.addAll(items);
        }
      }
    }

    public void afterSuccessfulCheckIn() {
      final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
      final ChangeList list = myChangeList;
      final List<Change> includedChanges = myIncludedChanges;
      if (list instanceof LocalChangeList) {
        final LocalChangeList localList = (LocalChangeList)list;
        if (includedChanges.containsAll(list.getChanges()) && !localList.isDefault() && !localList.isReadOnly()) {
          if (! myKeepChangeListAfterCommit) {
            changeListManager.removeChangeList(localList);
          }
        }
        else if (myConfiguration.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT && !includedChanges.containsAll(list.getChanges()) &&
                 localList.isDefault() && myAllOfDefaultChangeListChangesIncluded) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              ChangelistMoveOfferDialog dialog = new ChangelistMoveOfferDialog(myConfiguration);
              dialog.show();
              if (dialog.isOK()) {
                final Collection<Change> changes = changeListManager.getDefaultChangeList().getChanges();
                MoveChangesToAnotherListAction.askAndMove(myProject, changes.toArray(new Change[changes.size()]), null);
              }
            }
          }, ModalityState.NON_MODAL);
        }
      }
    }

    public void afterFailedCheckIn() {
      moveToFailedList(myChangeList, myCommitMessage, getChangesFailedToCommit(),
                       VcsBundle.message("commit.dialog.failed.commit.template", myChangeList.getName()), myProject);
    }

    public void doBeforeRefresh() {
      myAction = ApplicationManager.getApplication().runReadAction(new Computable<LocalHistoryAction>() {
        public LocalHistoryAction compute() {
          return LocalHistory.startAction(myProject, myActionName);
        }
      });
    }

    public Runnable postRefresh() {
      return new Runnable() {
        public void run() {
          myAction.finish();
          if (!myProject.isDisposed()) {
            for (FilePath path : myPathsToRefresh) {
              myDirtyScopeManager.fileDirty(path);
            }
            LocalHistory.putSystemLabel(myProject, myActionName + ": " + myCommitMessage);
          }
        }
      };
    }
  }

  private void markCommittingDocuments() {
    for (Change change : myIncludedChanges) {
      Document doc = ChangesUtil.getFilePath(change).getDocument();
      if (doc != null) {
        doc.putUserData(ChangeListManagerImpl.DOCUMENT_BEING_COMMITTED_KEY, new Object());
        myCommittingDocuments.add(doc);
      }
    }
  }

  private void unmarkCommittingDocuments() {
    for (Document doc : myCommittingDocuments) {
      doc.putUserData(ChangeListManagerImpl.DOCUMENT_BEING_COMMITTED_KEY, null);
    }
    myCommittingDocuments.clear();
  }

  private void commitCompleted(final List<VcsException> allExceptions, final GeneralCommitProcessor processor) {
    final List<VcsException> errors = collectErrors(allExceptions);
    final int errorsSize = errors.size();
    final int warningsSize = allExceptions.size() - errorsSize;

    if (errorsSize == 0) {
      for (CheckinHandler handler : myHandlers) {
        handler.checkinSuccessful();
      }

      processor.afterSuccessfulCheckIn();

    }
    else {
      for (CheckinHandler handler : myHandlers) {
        handler.checkinFailed(errors);
      }
    }

    if (errorsSize == 0 || warningsSize == 0) {
      final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null) {
        indicator.setText(VcsBundle.message("commit.dialog.completed.successfully"));
      }
    }

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (errorsSize > 0 && warningsSize > 0) {
          Messages.showErrorDialog(VcsBundle.message("message.text.commit.failed.with.errors.and.warnings"),
                                   VcsBundle.message("message.title.commit"));
        }
        else if (errorsSize > 0) {
          Messages.showErrorDialog(VcsBundle.message("message.text.commit.failed.with.errors"), VcsBundle.message("message.title.commit"));
        }
        else if (warningsSize > 0) {
          Messages
            .showErrorDialog(VcsBundle.message("message.text.commit.finished.with.warnings"), VcsBundle.message("message.title.commit"));
        }

        if (errorsSize > 0) {
          processor.afterFailedCheckIn();
        }
      }
    }, ModalityState.NON_MODAL);

  }

  public static void moveToFailedList(final ChangeList changeList,
                                      final String commitMessage,
                                      final List<Change> failedChanges,
                                      final String newChangelistName,
                                      final Project project) {
    // No need to move since we'll get exactly the same changelist.
    if (failedChanges.containsAll(changeList.getChanges())) return;

    final VcsConfiguration configuration = VcsConfiguration.getInstance(project);
    if (configuration.MOVE_TO_FAILED_COMMIT_CHANGELIST != VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
      final VcsShowConfirmationOption option = new VcsShowConfirmationOption() {
        public Value getValue() {
          return configuration.MOVE_TO_FAILED_COMMIT_CHANGELIST;
        }

        public void setValue(final Value value) {
          configuration.MOVE_TO_FAILED_COMMIT_CHANGELIST = value;
        }
      };
      boolean result = ConfirmationDialog.requestForConfirmation(option, project, VcsBundle.message("commit.failed.confirm.prompt"),
                                                                 VcsBundle.message("commit.failed.confirm.title"),
                                                                 Messages.getQuestionIcon());
      if (!result) return;
    }

    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    int index = 1;
    String failedListName = newChangelistName;
    while (changeListManager.findChangeList(failedListName) != null) {
      index++;
      failedListName = newChangelistName + " (" + index + ")";
    }

    final LocalChangeList failedList = changeListManager.addChangeList(failedListName, commitMessage);
    changeListManager.moveChangesTo(failedList, failedChanges.toArray(new Change[failedChanges.size()]));
  }

  private static List<VcsException> collectErrors(final List<VcsException> vcsExceptions) {
    final ArrayList<VcsException> result = new ArrayList<VcsException>();
    for (VcsException vcsException : vcsExceptions) {
      if (!vcsException.isWarning()) {
        result.add(vcsException);
      }
    }
    return result;
  }
}
