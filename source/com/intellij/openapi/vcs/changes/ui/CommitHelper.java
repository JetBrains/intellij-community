/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CommitHelper {
  private final Project myProject;
  
  
  private final ChangeList myChangeList;
  private final List<Change> myIncludedChanges;
  
  private final String myActionName;
  private final String myCommitMessage;
  
  private final CommitSession mySession;
  private final List<CheckinHandler> myHandlers;
  private final boolean myAllOfDefaultChangeListChangesIncluded;


  public CommitHelper(final Project project,
                      final ChangeList changeList,
                      final List<Change> includedChanges,
                      final String actionName,
                      final String commitMessage,
                      final CommitSession session,
                      final List<CheckinHandler> handlers,
                      final boolean allOfDefaultChangeListChangesIncluded) {
    myProject = project;
    myChangeList = changeList;
    myIncludedChanges = includedChanges;
    myActionName = actionName;
    myCommitMessage = commitMessage;
    mySession = session;
    myHandlers = handlers;
    myAllOfDefaultChangeListChangesIncluded = allOfDefaultChangeListChangesIncluded;
  }

  public void doCommit() {
    final Runnable checkinAction = new Runnable() {
      public void run() {
        final List<VcsException> vcsExceptions = new ArrayList<VcsException>();
        final List<Change> changesFailedToCommit = new ArrayList<Change>();

        ProgressManager.getInstance()
          .runProcessWithProgressSynchronously(checkinAction(vcsExceptions, changesFailedToCommit, myChangeList), myActionName, true, myProject);
      }
    };

    AbstractVcsHelper.getInstance(myProject).optimizeImportsAndReformatCode(collectVirtualFiles(),
                                                                            VcsConfiguration.getInstance(myProject), checkinAction, true);

  }

  private Collection<VirtualFile> collectVirtualFiles() {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (Change includedChange : myIncludedChanges) {
      final VirtualFile file = ChangesUtil.getFilePath(includedChange).getVirtualFile();
      if (file != null) {
        result.add(file);
      }
    }
    return result;
  }

  private Runnable checkinAction(final List<VcsException> vcsExceptions, final List<Change> changesFailedToCommit, final ChangeList changeList) {
    if (mySession != null) {
      return new Runnable() {
        public void run() {
          mySession.execute(myIncludedChanges, myCommitMessage);
        }
      };
    }

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
      final List<FilePath> pathsToRefresh = new ArrayList<FilePath>();
      ChangesUtil.processChangesByVcs(myProject, myIncludedChanges, new ChangesUtil.PerVcsProcessor<Change>() {
        public void process(AbstractVcs vcs, List<Change> changes) {
          final ChangeProvider provider = vcs.getChangeProvider();
          if (provider != null) {
            List<FilePath> paths = ChangesUtil.getPaths(changes);
            pathsToRefresh.addAll(paths);
            final List<VcsException> exceptions = provider.commit(changes, myCommitMessage);
            if (exceptions.size() > 0) {
              vcsExceptions.addAll(exceptions);
              changesFailedToCommit.addAll(changes);
            }
          }
        }
      });

      final LvcsAction lvcsAction = LocalVcs.getInstance(myProject).startAction(myActionName, "", true);
      VirtualFileManager.getInstance().refresh(true, new Runnable() {
        public void run() {
          lvcsAction.finish();
          FileStatusManager.getInstance(myProject).fileStatusesChanged();
          for (FilePath path : pathsToRefresh) {
            VcsDirtyScopeManager.getInstance(myProject).fileDirty(path);
          }
        }
      });
      AbstractVcsHelper.getInstance(myProject).showErrors(vcsExceptions, myActionName);
    }
    finally {
      commitCompleted(vcsExceptions, changeList, changesFailedToCommit, VcsConfiguration.getInstance(myProject), myHandlers, myCommitMessage);
    }
  }

  

  private void commitCompleted(final List<VcsException> allExceptions,
                               final ChangeList changeList,
                               final List<Change> failedChanges,
                               final VcsConfiguration config,
                               final List<CheckinHandler> checkinHandlers,
                               String commitMessage) {
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
        if (includedChanges.containsAll(list.getChanges()) && !localList.isDefault()) {
          changeListManager.removeChangeList(localList);
        }
        else if (config.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT && !includedChanges.containsAll(list.getChanges()) && localList.isDefault() && myAllOfDefaultChangeListChangesIncluded) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              ChangelistMoveOfferDialog dialog = new ChangelistMoveOfferDialog(config);
              dialog.show();
              if (dialog.isOK()) {
                final Collection<Change> changes = changeListManager.getDefaultChangelist().getChanges();
                MoveChangesToAnotherListAction.askAndMove(myProject, changes.toArray(new Change[changes.size()]));
              }
            }
          }, ModalityState.NON_MMODAL);
        }
      }
    }
    else {
      for (CheckinHandler handler : checkinHandlers) {
        handler.checkinFailed(errors);
      }

      moveToFailedList(changeList, commitMessage, failedChanges, VcsBundle.message("commit.dialog.failed.commit.template", changeList.getName()),
                       myProject);
    }

    config.ERROR_OCCURED = errorsSize > 0;


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

      }
    }, ModalityState.NON_MMODAL);

  }

  public static void moveToFailedList(final ChangeList changeList,
                                final String commitMessage,
                                final List<Change> failedChanges,
                                final String newChangelistName,
                                final Project project) {
    // No need to move since we'll get exactly the same changelist.
    if (failedChanges.containsAll(changeList.getChanges())) return;

    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    final LocalChangeList failedList =
      changeListManager.addChangeList(newChangelistName);

    failedList.setComment(commitMessage);
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
