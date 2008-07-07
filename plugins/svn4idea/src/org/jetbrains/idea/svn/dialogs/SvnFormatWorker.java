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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;

public class SvnFormatWorker extends Task.Backgroundable {
  private List<Throwable> myExceptions;
  private final Project myProject;
  private final WorkingCopyFormat myNewFormat;
  private final List<WCInfo> myWcInfos;

  public SvnFormatWorker(final Project project, final WorkingCopyFormat newFormat, final List<WCInfo> wcInfos) {
    super(project, SvnBundle.message("action.change.wcopy.format.task.title"), false, DEAF);
    myProject = project;
    myNewFormat = newFormat;
    myExceptions = new ArrayList<Throwable>();
    myWcInfos = wcInfos;
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
        int result = Messages.showYesNoCancelDialog(SvnBundle.message("upgrade.format.to15.clarify.for.outside.copies.text", path),
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
    ProjectLevelVcsManager.getInstance(myProject).stopBackgroundVcsOperation();

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
    ProjectLevelVcsManager.getInstance(myProject).startBackgroundVcsOperation();
    indicator.setIndeterminate(true);
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    final SVNWCClient wcClient = vcs.createWCClient();

    try {
      for (WCInfo wcInfo : myWcInfos) {
        File path = new File(wcInfo.getPath());
        if (! wcInfo.isIsWcRoot()) {
          path = SvnUtil.getWorkingCopyRoot(path);
        }
        indicator.setText(SvnBundle.message("action.change.wcopy.format.task.progress.text", path.getAbsolutePath(),
                                            SvnUtil.formatRepresentation(wcInfo.getFormat()), SvnUtil.formatRepresentation(myNewFormat)));
        try {
          wcClient.doSetWCFormat(path, myNewFormat.getFormat());
        } catch (Throwable e) {
          myExceptions.add(e);
        }
      }
    }
    finally {
      ApplicationManager.getApplication().getMessageBus().syncPublisher(SvnMapDialog.WC_CONVERTED).run();
    }
  }
}
