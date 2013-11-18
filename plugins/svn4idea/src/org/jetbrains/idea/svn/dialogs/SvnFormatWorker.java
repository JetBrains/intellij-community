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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
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
import org.jetbrains.idea.svn.api.ClientFactory;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class SvnFormatWorker extends Task.Backgroundable {
  private List<Throwable> myExceptions;
  private final Project myProject;
  private final WorkingCopyFormat myNewFormat;
  private final List<WCInfo> myWcInfos;
  private List<LocalChangeList> myBeforeChangeLists;
  private final SvnVcs myVcs;

  public SvnFormatWorker(final Project project, final WorkingCopyFormat newFormat, final List<WCInfo> wcInfos) {
    super(project, SvnBundle.message("action.change.wcopy.format.task.title"), false, DEAF);
    myProject = project;
    myNewFormat = newFormat;
    myExceptions = new ArrayList<Throwable>();
    myWcInfos = wcInfos;
    myVcs = SvnVcs.getInstance(myProject);
  }

  public SvnFormatWorker(final Project project, final WorkingCopyFormat newFormat, final WCInfo wcInfo) {
    this(project, newFormat, Collections.singletonList(wcInfo));
  }

  public void checkForOutsideCopies() {
    boolean canceled = false;
    for (Iterator<WCInfo> iterator = myWcInfos.iterator(); iterator.hasNext();) {
      final WCInfo wcInfo = iterator.next();
      if (! wcInfo.isIsWcRoot()) {
        File path = new File(wcInfo.getPath());
        path = SvnUtil.getWorkingCopyRoot(path);
        int result = Messages.showYesNoCancelDialog(SvnBundle.message("upgrade.format.clarify.for.outside.copies.text", path),
                                                    SvnBundle.message("action.change.wcopy.format.task.title"),
                                                    Messages.getWarningIcon());
        if (DialogWrapper.CANCEL_EXIT_CODE == result) {
          canceled = true;
          break;
        } else if (DialogWrapper.OK_EXIT_CODE != result) {
          // no - for this copy only. maybe other
          iterator.remove();
        }
      }
    }
    if (canceled) {
      myWcInfos.clear();
    }
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
      final List<String> messages = new ArrayList<String>();
      for (Throwable exception : myExceptions) {
        messages.add(exception.getMessage());
      }
      AbstractVcsHelper.getInstance(myProject)
          .showErrors(Collections.singletonList(new VcsException(messages)), SvnBundle.message("action.change.wcopy.format.task.title"));
    }
  }

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
          String upgradeMessage = SvnBundle.message("action.change.wcopy.format.task.progress.text", path.getAbsolutePath(),
                                                    SvnUtil.formatRepresentation(wcInfo.getFormat()),
                                                    SvnUtil.formatRepresentation(myNewFormat));
          ISVNEventHandler handler = createUpgradeHandler(indicator, cleanupMessage, upgradeMessage);

          getFactory(path, myNewFormat).createUpgradeClient().upgrade(path, myNewFormat, handler);
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

      ApplicationManager.getApplication().getMessageBus().syncPublisher(SvnVcs.WC_CONVERTED).run();
    }
  }

  @NotNull
  private ClientFactory getFactory(@NotNull File path, @NotNull WorkingCopyFormat format) throws VcsException {
    ClientFactory factory = myVcs.getFactory(path);
    ClientFactory otherFactory = myVcs.getOtherFactory(factory);
    List<WorkingCopyFormat> factoryFormats = factory.createUpgradeClient().getSupportedFormats();
    List<WorkingCopyFormat> otherFactoryFormats = otherFactory.createUpgradeClient().getSupportedFormats();

    return factoryFormats.contains(format) || !otherFactoryFormats.contains(format) ? factory : otherFactory;
  }

  private static ISVNEventHandler createUpgradeHandler(@NotNull final ProgressIndicator indicator,
                                                       @NotNull final String cleanupMessage,
                                                       @NotNull final String upgradeMessage) {
    return new ISVNEventHandler() {
      @Override
      public void handleEvent(SVNEvent event, double progress) throws SVNException {
        if (event.getFile() != null) {
          if (SVNEventAction.UPGRADED_PATH.equals(event.getAction())) {
            indicator.setText2("Upgraded path " + VcsUtil.getPathForProgressPresentation(event.getFile()));
          }
          // fake event indicating cleanup start
          if (SVNEventAction.UPDATE_STARTED.equals(event.getAction())) {
            indicator.setText(cleanupMessage);
          }
          // fake event indicating upgrade start
          if (SVNEventAction.UPDATE_COMPLETED.equals(event.getAction())) {
            indicator.setText(upgradeMessage);
          }
        }
      }

      @Override
      public void checkCancelled() throws SVNCancelException {
        indicator.checkCanceled();
      }
    };
  }
}
