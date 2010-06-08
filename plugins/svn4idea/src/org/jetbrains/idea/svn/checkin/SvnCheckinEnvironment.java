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
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.ui.ChangesViewBalloonProblemNotifier;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

public class SvnCheckinEnvironment implements CheckinEnvironment {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.checkin.SvnCheckinEnvironment");
  private final SvnVcs mySvnVcs;

  public SvnCheckinEnvironment(SvnVcs svnVcs) {
    mySvnVcs = svnVcs;
  }

  public RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel,
                                                             PairConsumer<Object, Object> additionalDataConsumer) {
    return new KeepLocksComponent(panel);
  }

  @Nullable
  public String getDefaultMessageFor(FilePath[] filesToCheckin) {
    return null;
  }

  @Nullable
  public String getHelpId() {
    return null;
  }


  private List<VcsException> commitInt(List<File> paths, final String comment, final boolean force, final boolean recursive) {
    final List<VcsException> exception = new ArrayList<VcsException>();
    final Collection<File> committables = getCommitables(paths);

    final SVNCommitClient committer = mySvnVcs.createCommitClient();

    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    final Collection<VirtualFile> deletedFiles = new ArrayList<VirtualFile>();
    if (progress != null) {
      committer.setEventHandler(new ISVNEventHandler() {
        public void handleEvent(final SVNEvent event, double p) {
          final String path = SvnUtil.getPathForProgress(event);
          if (path == null) {
            return;
          }
          if (event.getAction() == SVNEventAction.COMMIT_ADDED) {
            progress.setText2(SvnBundle.message("progress.text2.adding", path));
          }
          else if (event.getAction() == SVNEventAction.COMMIT_DELETED) {
            @NonNls final String filePath = "file://" + event.getFile().getAbsolutePath().replace(File.separatorChar, '/');
            VirtualFile vf = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
              @Nullable public VirtualFile compute() {
                return VirtualFileManager.getInstance().findFileByUrl(filePath);
              }
            });
            if (vf != null) {
              deletedFiles.add(vf);
            }
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
          // do not need COMMIT_COMPLETED: same info is get another way
        }

        public void checkCancelled() throws SVNCancelException {
          try {
            progress.checkCanceled();
          }
          catch(ProcessCanceledException ex) {
            throw new SVNCancelException();
          }
        }
      });
    }

    if (progress != null) {
      doCommit(committables, progress, committer, comment, force, recursive, exception);
    }
    else if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          ProgressIndicator p = ProgressManager.getInstance().getProgressIndicator();
          doCommit(committables, p, committer, comment, force, recursive, exception);
        }
      }, SvnBundle.message("progress.title.commit"), false, mySvnVcs.getProject());
    }
    else {
      doCommit(committables, progress, committer, comment, force, recursive, exception);
    }

    for(VirtualFile f : deletedFiles) {
      f.putUserData(VirtualFile.REQUESTOR_MARKER, this);
    }
    return exception;
  }

  private void doCommit(Collection<File> committables,
                        ProgressIndicator progress,
                        SVNCommitClient committer,
                        String comment,
                        boolean force,
                        boolean recursive,
                        List<VcsException> exception) {
    if (committables.isEmpty()) {
      return;
    }
    File[] pathsToCommit = (File[])committables.toArray(new File[committables.size()]);
    boolean keepLocks = SvnConfiguration.getInstanceChecked(mySvnVcs.getProject()).isKeepLocks();
    SVNCommitPacket[] commitPackets = null;
    SVNCommitInfo[] results;
    try {
      commitPackets = committer.doCollectCommitItems(pathsToCommit, keepLocks, force, recursive, true);
      results = committer.doCommit(commitPackets, keepLocks, comment);
      commitPackets = null;
    }
    catch (SVNException e) {
      // exception on collecting commitables.
      exception.add(new VcsException(e));
      LOG.info(e);
      return;
    }
    finally {
      if (commitPackets != null) {
        for (int i = 0; i < commitPackets.length; i++) {
          SVNCommitPacket commitPacket = commitPackets[i];
          try {
            commitPacket.dispose();
          }
          catch (SVNException e) {
            //
          }
        }
      }
    }
    final StringBuffer committedRevisions = new StringBuffer();
    for (SVNCommitInfo result : results) {
      if (result.getErrorMessage() != null) {
        exception.add(new VcsException(result.getErrorMessage().getFullMessage()));
      }
      else if (result != SVNCommitInfo.NULL && result.getNewRevision() > 0) {
        if (committedRevisions.length() > 0) {
          committedRevisions.append(", ");
        }
        committedRevisions.append(result.getNewRevision());
      }
    }
    if (committedRevisions.length() > 0) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          new ChangesViewBalloonProblemNotifier(mySvnVcs.getProject(),
                                                SvnBundle.message("status.text.comitted.revision", committedRevisions),
                                                MessageType.INFO).run();
        }
      });
    }
  }

  private static class Adder {
    private final Collection<File> myResult = new ArrayList<File>();
    private final Set<String> myDuplicatesControlSet = new HashSet<String>();

    public void add(final File file) {
      final String path = file.getAbsolutePath();
      if (! myDuplicatesControlSet.contains(path)) {
        myResult.add(file);
        myDuplicatesControlSet.add(path);
      }
    }

    public Collection<File> getResult() {
      return myResult;
    }
  }

  private Collection<File> getCommitables(List<File> paths) {
    final Adder adder = new Adder();

    SVNStatusClient statusClient = mySvnVcs.createStatusClient();
    for (File path : paths) {
      File file = path.getAbsoluteFile();
      adder.add(file);
      if (file.getParentFile() != null) {
        addParents(statusClient, file.getParentFile(), adder);
      }
    }
    return adder.getResult();
  }

  private static void addParents(SVNStatusClient statusClient, File file, final Adder adder) {
    SVNStatus status;
    try {
      status = statusClient.doStatus(file, false);
    }
    catch (SVNException e) {
      return;
    }
    if (status != null &&
        (status.getContentsStatus() == SVNStatusType.STATUS_ADDED ||
         status.getContentsStatus() == SVNStatusType.STATUS_REPLACED)) {
      // file should be added
      adder.add(file);
      file = file.getParentFile();
      if (file != null) {
        addParents(statusClient, file, adder);
      }
    }
  }

  private static List<File> collectPaths(final List<Change> changes) {
    // case sensitive..
    ArrayList<File> result = new ArrayList<File>();

    final Set<String> pathesSet = new HashSet<String>();
    for (Change change : changes) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      final ContentRevision afterRevision = change.getAfterRevision();
      if (beforeRevision != null) {
        pathesSet.add(beforeRevision.getFile().getIOFile().getAbsolutePath());
      }
      if (afterRevision != null) {
        pathesSet.add(afterRevision.getFile().getIOFile().getAbsolutePath());
      }
    }

    for (String s : pathesSet) {
      result.add(new File(s));
    }
    return result;
  }

  public String getCheckinOperationName() {
    return SvnBundle.message("checkin.operation.name");
  }

  public List<VcsException> commit(List<Change> changes, String preparedComment, @NotNull NullableFunction<Object, Object> parametersHolder) {
    return commitInt(collectPaths(changes), preparedComment, true, false);
  }

  public List<VcsException> commit(List<Change> changes, String preparedComment) {
    return commit(changes, preparedComment, NullableFunction.NULL);
  }

  public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> filePaths) {
    List<VcsException> exceptions = new ArrayList<VcsException>();
    final SVNWCClient wcClient = mySvnVcs.createWCClient();

    List<File> files = ChangesUtil.filePathsToFiles(filePaths);
    for (File file : files) {
      try {
        wcClient.doDelete(file, true, false);
      }
      catch (SVNException e) {
        exceptions.add(new VcsException(e));
      }
    }

    return exceptions;
  }

  public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
    final List<VcsException> result = new ArrayList<VcsException>();
    final SVNWCClient wcClient = mySvnVcs.createWCClient();

    final List<SVNException> exceptionList = scheduleUnversionedFilesForAddition(wcClient, files);
    for (SVNException svnException : exceptionList) {
      result.add(new VcsException(svnException));
    }
    return result;
  }

  public static List<SVNException> scheduleUnversionedFilesForAddition(SVNWCClient wcClient, List<VirtualFile> files) {
    return scheduleUnversionedFilesForAddition(wcClient, files, false);
  }

  public static List<SVNException> scheduleUnversionedFilesForAddition(SVNWCClient wcClient, List<VirtualFile> files, final boolean recursive) {
    List<SVNException> exceptions = new ArrayList<SVNException>();

    Collections.sort(files, FilePathComparator.getInstance());

    for (VirtualFile file : files) {
      try {
        wcClient.doAdd(new File(FileUtil.toSystemDependentName(file.getPath())), true, false, true, recursive);
      }
      catch (SVNException e) {
        exceptions.add(e);
      }
    }

    return exceptions;
  }

  public boolean keepChangeListAfterCommit(ChangeList changeList) {
    return false;
  }

  private class KeepLocksComponent implements RefreshableOnComponent {
    private final JCheckBox myKeepLocksBox;
    private boolean myIsKeepLocks;
    private final JPanel myPanel;

    public KeepLocksComponent(final Refreshable panel) {

      myPanel = new JPanel(new BorderLayout());
      myKeepLocksBox = new JCheckBox(SvnBundle.message("checkbox.chckin.keep.files.locked"));
      myKeepLocksBox.setSelected(myIsKeepLocks);

      myPanel.add(myKeepLocksBox, BorderLayout.CENTER);
    }

    public JComponent getComponent() {
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
