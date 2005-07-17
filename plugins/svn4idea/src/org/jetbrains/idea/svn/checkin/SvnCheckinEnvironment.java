/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.actionSystem.AnAction;
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.DialogUtil;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
    return new SvnRollbackProvider(selectedRevisions, mySvnVcs, containsExcluded);
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
            progress.setText2("Adding '" + path + "'");
          }
          else if (event.getAction() == SVNEventAction.COMMIT_DELETED) {
            progress.setText2("Deleting '" + path + "'");
          }
          else if (event.getAction() == SVNEventAction.COMMIT_MODIFIED) {
            progress.setText2("Sending '" + path + "'");
          }
          else if (event.getAction() == SVNEventAction.COMMIT_REPLACED) {
            progress.setText2("Replacing '" + path + "'");
          }
          else if (event.getAction() == SVNEventAction.COMMIT_DELTA_SENT) {
            progress.setText2("Transmitting delta for  '" + path + "'");
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
      }, "Commit", false, mySvnVcs.getProject());
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
        progress.setText("Committing changes below '" + root.getAbsolutePath() + "'");
      }

      File[] filesArray = (File[])files.toArray(new File[files.size()]);
      boolean keepLocks = myKeepLocksComponent != null && myKeepLocksComponent.isKeepLocks();
      try {
        SVNCommitInfo result = committer.doCommit(filesArray, keepLocks, comment, force, recursive);
        if (result != SVNCommitInfo.NULL && result.getNewRevision() >= 0) {
          WindowManager.getInstance().getStatusBar(mySvnVcs.getProject()).setInfo("Committed revision " + result.getNewRevision() + ".");
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
    return "Checkin";
  }

  private class KeepLocksComponent implements RefreshableOnComponent {
    private JCheckBox myKeepLocksBox;
    private boolean myIsKeepLocks;
    private JPanel myPanel;

    public JComponent getComponent() {
      if (myPanel == null) {
        myPanel = new JPanel(new BorderLayout());
        myKeepLocksBox = new JCheckBox("Keep files &locked");
        myKeepLocksBox.setSelected(myIsKeepLocks);
        DialogUtil.registerMnemonic(myKeepLocksBox);

        myPanel.add(myKeepLocksBox, BorderLayout.CENTER);
        myPanel.setBorder(new TitledBorder("Subversion"));
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
