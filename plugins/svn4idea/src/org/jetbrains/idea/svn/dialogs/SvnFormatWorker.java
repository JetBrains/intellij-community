// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.EventAction;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.api.ProgressTracker;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SvnFormatWorker extends Task.Backgroundable {

  private final List<Throwable> myExceptions;
  private final Project myProject;
  @NotNull private final WorkingCopyFormat myNewFormat;
  private final List<WCInfo> myWcInfos;
  private List<LocalChangeList> myBeforeChangeLists;
  private final SvnVcs myVcs;

  public SvnFormatWorker(final Project project, @NotNull final WorkingCopyFormat newFormat, final List<WCInfo> wcInfos) {
    super(project, SvnBundle.message("action.change.wcopy.format.task.title"), false, DEAF);
    myProject = project;
    myNewFormat = newFormat;
    myExceptions = new ArrayList<>();
    myWcInfos = wcInfos;
    myVcs = SvnVcs.getInstance(myProject);
  }

  public SvnFormatWorker(final Project project, @NotNull final WorkingCopyFormat newFormat, final WCInfo wcInfo) {
    this(project, newFormat, Collections.singletonList(wcInfo));
  }

  public boolean haveStuffToConvert() {
    return ! myWcInfos.isEmpty();
  }

  @Override
  public void onCancel() {
    onSuccess();
  }

  @Override
  public void onSuccess() {
    if (myProject.isDisposed()) {
      return;
    }

    if (! myExceptions.isEmpty()) {
      final List<String> messages = new ArrayList<>();
      for (Throwable exception : myExceptions) {
        messages.add(exception.getMessage());
      }
      AbstractVcsHelper.getInstance(myProject)
          .showErrors(Collections.singletonList(new VcsException(messages)), SvnBundle.message("action.change.wcopy.format.task.title"));
    }
  }

  @Override
  public void run(@NotNull final ProgressIndicator indicator) {
    ProjectLevelVcsManager.getInstanceChecked(myProject).startBackgroundVcsOperation();
    indicator.setIndeterminate(true);
    final boolean supportsChangelists = myNewFormat.supportsChangelists();
    if (supportsChangelists) {
      myBeforeChangeLists = ChangeListManager.getInstance(myProject).getChangeListsCopy();
    }

    try {
      for (WCInfo wcInfo : myWcInfos) {
        File path = new File(wcInfo.getPath());
        if (! wcInfo.isIsWcRoot()) {
          path = SvnUtil.getWorkingCopyRoot(path);
        }
        try {
          String cleanupMessage = SvnBundle.message("action.Subversion.cleanup.progress.text", path.getAbsolutePath());
          String upgradeMessage =
            SvnBundle.message("action.change.wcopy.format.task.progress.text", path.getAbsolutePath(), wcInfo.getFormat(), myNewFormat);
          ProgressTracker handler = createUpgradeHandler(indicator, cleanupMessage, upgradeMessage);

          myVcs.getFactory(path).createUpgradeClient().upgrade(path, myNewFormat, handler);
        } catch (Throwable e) {
          myExceptions.add(e);
        }
      }
    }
    finally {
      ProjectLevelVcsManager.getInstance(myProject).stopBackgroundVcsOperation();

      // to map to native
      if (supportsChangelists) {
        SvnVcs.getInstance(myProject).processChangeLists(myBeforeChangeLists);
      }

      BackgroundTaskUtil.syncPublisher(SvnVcs.WC_CONVERTED).run();
    }
  }

  private static ProgressTracker createUpgradeHandler(@NotNull final ProgressIndicator indicator,
                                                       @NotNull final String cleanupMessage,
                                                       @NotNull final String upgradeMessage) {
    return new ProgressTracker() {
      @Override
      public void consume(ProgressEvent event) {
        if (event.getFile() != null) {
          if (EventAction.UPGRADED_PATH.equals(event.getAction())) {
            indicator.setText2("Upgraded path " + VcsUtil.getPathForProgressPresentation(event.getFile()));
          }
          // fake event indicating cleanup start
          if (EventAction.UPDATE_STARTED.equals(event.getAction())) {
            indicator.setText(cleanupMessage);
          }
          // fake event indicating upgrade start
          if (EventAction.UPDATE_COMPLETED.equals(event.getAction())) {
            indicator.setText(upgradeMessage);
          }
        }
      }

      @Override
      public void checkCancelled() throws ProcessCanceledException {
        indicator.checkCanceled();
      }
    };
  }
}
