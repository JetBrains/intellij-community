/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectDialogImplementer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.RollbackProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.DifferenceType;
import com.intellij.openapi.vcs.checkin.RevisionsFactory;
import com.intellij.openapi.vcs.checkin.VcsOperation;
import com.intellij.openapi.vcs.checkin.changeListBasedCheckin.CommitChangeOperation;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vcs.versions.AbstractRevisions;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.SvnBundle;
import com.intellij.util.ui.DialogUtil;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.*;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

public class SvnCheckinEnvironment implements CheckinEnvironment {
  private final SvnVcs mySvnVcs;
  private KeepLocksComponent myKeepLocksComponent;

  public boolean showCheckinDialogInAnyCase() {
    return false;
  }

  public SvnCheckinEnvironment(SvnVcs svnVcs) {
    mySvnVcs = svnVcs;
  }

  public RevisionsFactory getRevisionsFactory() {
    return new SvnRevisionsFactory(mySvnVcs);
  }

  public RollbackProvider createRollbackProviderOn(AbstractRevisions[] selectedRevisions, final boolean containsExcluded) {
    return new SvnRollbackProvider(selectedRevisions, mySvnVcs);
  }

  public DifferenceType[] getAdditionalDifferenceTypes() {
    return new DifferenceType[0];
  }

  public ColumnInfo[] getAdditionalColumns(int index) {
    return new ColumnInfo[0];
  }

  public RefreshableOnComponent createAdditionalOptionsPanelForCheckinProject(Refreshable panel) {
    myKeepLocksComponent = new KeepLocksComponent();
    return myKeepLocksComponent;
  }

  public RefreshableOnComponent createAdditionalOptionsPanelForCheckinFile(Refreshable panel) {
    myKeepLocksComponent = new KeepLocksComponent();
    return myKeepLocksComponent;
  }

  public RefreshableOnComponent createAdditionalOptionsPanel(Refreshable panel, boolean checkinProject) {
    return null;
  }

  public String getDefaultMessageFor(FilePath[] filesToCheckin) {
    return null;
  }

  public void onRefreshFinished() {
  }

  public void onRefreshStarted() {
  }

  public AnAction[] getAdditionalActions(int index) {
    return new AnAction[0];
  }

  public String prepareCheckinMessage(String text) {
    return text;
  }

  public String getHelpId() {
    return null;
  }

  public List<VcsException> commit(CheckinProjectDialogImplementer dialog, Project project) {
    return commitInt(collectFilePaths(dialog.getCheckinProjectPanel().getCheckinOperations(this)),
                     dialog.getPreparedComment(this), true, false);
  }

  public List<VcsException> commit(FilePath[] roots, Project project, String preparedComment) {
    return commitInt(collectPaths(roots), preparedComment, false, true);
  }


  private List<VcsException> commitInt(List<String> paths, final String comment, final boolean force, final boolean recursive) {
    final List exception = new ArrayList<VcsException>();
    final Map committables = getCommitables(paths);

    final SVNCommitClient committer;
    try {
      committer = mySvnVcs.createCommitClient();
    }
    catch (SVNException e) {
      exception.add(new VcsException(e));
      return exception;
    }

    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) {
      committer.setEventHandler(new ISVNEventHandler() {
        public void handleEvent(SVNEvent event, double p) {
          String path = event.getFile() != null ? event.getFile().getName() : event.getPath();
          if (path == null) {
            return;
          }
          if (event.getAction() == SVNEventAction.COMMIT_ADDED) {
            progress.setText2(SvnBundle.message("progress.text2.adding", path));
          }
          else if (event.getAction() == SVNEventAction.COMMIT_DELETED) {
            progress.setText2(SvnBundle.message("progress.text2.deleting", path));
          }
          else if (event.getAction() == SVNEventAction.COMMIT_MODIFIED) {
            progress.setText2(SvnBundle.message("progress.text2.sending", path));
          }
          else if (event.getAction() == SVNEventAction.COMMIT_REPLACED) {
            progress.setText2(SvnBundle.message("progress.text2.replacing", path));
          }
          else if (event.getAction() == SVNEventAction.COMMIT_DELTA_SENT) {
            progress.setText2(SvnBundle.message("progress.text2.transmitting.delta", path));
          }
        }

        public void checkCancelled() {
        }
      });
    }

    if (progress != null) {
      doCommit(committables, progress, committer, comment, force, recursive, exception);
    }
    else if (ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          ProgressIndicator p = ProgressManager.getInstance().getProgressIndicator();
          doCommit(committables, p, committer, comment, force, recursive, exception);
        }
      }, SvnBundle.message("progress.title.commit"), false, mySvnVcs.getProject());
    }
    else {
      doCommit(committables, progress, committer, comment, force, recursive, exception);
    }
    return exception;
  }

  private void doCommit(Map committables,
                        ProgressIndicator progress,
                        SVNCommitClient committer,
                        String comment,
                        boolean force,
                        boolean recursive,
                        List exception) {
    for (Iterator roots = committables.keySet().iterator(); roots.hasNext();) {
      File root = (File)roots.next();
      Collection files = (Collection)committables.get(root);
      if (files.isEmpty()) {
        continue;
      }
      if (progress != null) {
        progress.setText(SvnBundle.message("progress.text.committing.changes.below", root.getAbsolutePath()));
      }

      File[] filesArray = (File[])files.toArray(new File[files.size()]);
      boolean keepLocks = myKeepLocksComponent != null && myKeepLocksComponent.isKeepLocks();
      try {
        SVNCommitInfo result = committer.doCommit(filesArray, keepLocks, comment, force, recursive);
        if (result != SVNCommitInfo.NULL && result.getNewRevision() >= 0) {
          WindowManager.getInstance().getStatusBar(mySvnVcs.getProject()).setInfo(
            SvnBundle.message("status.text.committed.revision", result.getNewRevision()));
        }
      }
      catch (SVNException e) {
        exception.add(new VcsException(e));
      }
    }
  }

  private Map getCommitables(List<String> paths) {
    Map result = new HashMap();
    for (Iterator<String> p = paths.iterator(); p.hasNext();) {
      String path = p.next();
      File file = new File(path).getAbsoluteFile();
      File parent = file;
      if (file.isFile()) {
        parent = file.getParentFile();
      }
      File wcRoot = SVNWCUtil.getWorkingCopyRoot(parent, true);
      if (!result.containsKey(wcRoot)) {
        result.put(wcRoot, new ArrayList());
      }
      ((Collection)result.get(wcRoot)).add(file);
    }
    return result;
  }

  private List<String> collectPaths(FilePath[] roots) {
    ArrayList<String> result = new ArrayList<String>();
    for (int i = 0; i < roots.length; i++) {
      FilePath file = roots[i];
      result.add(file.getPath());
    }
    return result;
  }

  private List<String> collectFilePaths(List<VcsOperation> checkinOperations) {
    ArrayList<String> result = new ArrayList<String>();
    for (Iterator<VcsOperation> iterator = checkinOperations.iterator(); iterator.hasNext();) {
      CommitChangeOperation<SVNStatus> operation = (CommitChangeOperation<SVNStatus>)iterator.next();
      result.add(operation.getFile().getAbsolutePath());
    }
    return result;
  }

  public String getCheckinOperationName() {
    return SvnBundle.message("checkin.operation.name");
  }

  private class KeepLocksComponent implements RefreshableOnComponent {
    private JCheckBox myKeepLocksBox;
    private boolean myIsKeepLocks;
    private JPanel myPanel;

    public JComponent getComponent() {
      if (myPanel == null) {
        myPanel = new JPanel(new BorderLayout());
        myKeepLocksBox = new JCheckBox(SvnBundle.message("checkbox.chckin.keep.files.locked"));
        myKeepLocksBox.setSelected(myIsKeepLocks);

        myPanel.add(myKeepLocksBox, BorderLayout.CENTER);
        myPanel.setBorder(new TitledBorder(SvnBundle.message("border.show.changes.dialog.subversion.group")));
      }
      return myPanel;
    }

    public boolean isKeepLocks() {
      return myKeepLocksBox != null && myKeepLocksBox.isSelected();
    }

    public void refresh() {
    }

    public void saveState() {
      mySvnVcs.getSvnConfiguration().setKeepLocks(isKeepLocks());
    }

    public void restoreState() {
      myIsKeepLocks = mySvnVcs.getSvnConfiguration().isKeepLocks();
    }
  }

}
