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
    final List<VcsException> vcsExceptions = new ArrayList<VcsException>();
    final List<Change> changesFailedToCommit = new ArrayList<Change>();

    final Runnable action = checkinAction(vcsExceptions, changesFailedToCommit, myChangeList);
    if (myForceSyncCommit) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(action, myActionName, true, myProject);
      return doesntContainErrors(vcsExceptions);
    }
    else {
      Task.Backgroundable task =
        new Task.Backgroundable(myProject, myActionName, true, myConfiguration.getCommitOption()) {
          public void run(final ProgressIndicator indicator) {
            final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
            vcsManager.startBackgroundVcsOperation();
            try {
              action.run();
            }
            finally {
              vcsManager.stopBackgroundVcsOperation();
            }
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

  private Runnable checkinAction(final List<VcsException> vcsExceptions,
                                 final List<Change> changesFailedToCommit,
                                 final ChangeList changeList) {
    return new Runnable() {
      public void run() {
        performCommit(vcsExceptions, changesFailedToCommit, changeList);
      }
    };
  }

  private void performCommit(final List<VcsException> vcsExceptions,
                             final List<Change> changesFailedToCommit,
                             final ChangeList changeList) {
    try {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          markCommittingDocuments();
        }
      });
      final List<FilePath> pathsToRefresh = new ArrayList<FilePath>();
      ChangesUtil.processChangesByVcs(myProject, myIncludedChanges, new ChangesUtil.PerVcsProcessor<Change>() {
        public void process(AbstractVcs vcs, List<Change> changes) {
          final CheckinEnvironment environment = vcs.getCheckinEnvironment();
          if (environment != null) {
            Collection<FilePath> paths = ChangesUtil.getPaths(changes);
            pathsToRefresh.addAll(paths);
            final List<VcsException> exceptions = environment.commit(changes, myCommitMessage);
            if (exceptions != null && exceptions.size() > 0) {
              vcsExceptions.addAll(exceptions);
              changesFailedToCommit.addAll(changes);
            }
          }
        }
      });

      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          unmarkCommittingDocuments();
        }
      });

      final LocalHistoryAction action = ApplicationManager.getApplication().runReadAction(new Computable<LocalHistoryAction>() {
        public LocalHistoryAction compute() {
          return LocalHistory.startAction(myProject, myActionName);
        }
      });
      VirtualFileManager.getInstance().refresh(true, new Runnable() {
        public void run() {
          action.finish();
          for (FilePath path : pathsToRefresh) {
            myDirtyScopeManager.fileDirty(path);
          }
          LocalHistory.putSystemLabel(myProject, myActionName + ": " + myCommitMessage);
        }
      });
      AbstractVcsHelper.getInstance(myProject).showErrors(vcsExceptions, myActionName);
    }
    finally {
      commitCompleted(vcsExceptions, changeList, changesFailedToCommit, myHandlers, myCommitMessage);
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

  private void commitCompleted(final List<VcsException> allExceptions,
                               final ChangeList changeList,
                               final List<Change> failedChanges,
                               final List<CheckinHandler> checkinHandlers,
                               final String commitMessage) {
    final List<VcsException> errors = collectErrors(allExceptions);
    final int errorsSize = errors.size();
    final int warningsSize = allExceptions.size() - errorsSize;

    if (errorsSize == 0) {
      for (CheckinHandler handler : checkinHandlers) {
        handler.checkinSuccessful();
      }
      final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
      final ChangeList list = myChangeList;
      final List<Change> includedChanges = myIncludedChanges;
      if (list instanceof LocalChangeList) {
        final LocalChangeList localList = (LocalChangeList)list;
        if (includedChanges.containsAll(list.getChanges()) && !localList.isDefault() && !localList.isReadOnly()) {
          changeListManager.removeChangeList(localList);
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
    else {
      for (CheckinHandler handler : checkinHandlers) {
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
          moveToFailedList(changeList, commitMessage, failedChanges,
                           VcsBundle.message("commit.dialog.failed.commit.template", changeList.getName()), myProject);
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
