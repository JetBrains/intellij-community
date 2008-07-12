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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
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

  public RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel) {
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
        public void handleEvent(SVNEvent event, double p) {
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
          else if (event.getAction() == SVNEventAction.COMMIT_COMPLETED) {
            WindowManager.getInstance().getStatusBar(mySvnVcs.getProject())
              .setInfo(SvnBundle.message("status.text.comitted.revision", event.getRevision()));
          }
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
    boolean keepLocks = SvnConfiguration.getInstance(mySvnVcs.getProject()).isKeepLocks();
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
    StringBuffer committedRevisions = new StringBuffer();
    for (int i = 0; i < results.length; i++) {
      SVNCommitInfo result = results[i];
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
      WindowManager.getInstance().getStatusBar(mySvnVcs.getProject()).setInfo(
        SvnBundle.message("status.text.committed.revision", committedRevisions));
    }
  }

  private Collection<File> getCommitables(List<File> paths) {
    Collection<File> result = new HashSet<File>();
    SVNStatusClient statusClient = mySvnVcs.createStatusClient();
    for (File path : paths) {
      File file = path.getAbsoluteFile();
      result.add(file);
      if (file.getParentFile() != null) {
        addParents(statusClient, file.getParentFile(), result);
      }
    }
    return result;
  }

  private static void addParents(SVNStatusClient statusClient, File file, Collection<File> files) {
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
      files.add(file);
      file = file.getParentFile();
      if (file != null) {
        addParents(statusClient, file, files);
      }
    }
  }

  private static List<File> collectPaths(Collection<FilePath> roots) {
    ArrayList<File> result = new ArrayList<File>();
    for (FilePath file : roots) {
      // if file is scheduled for addition[r] and its parent is also scheduled for additio[r] ->
      // then add parents till versioned file is met. same for 'copied' files.
      result.add(file.getIOFile());
    }
    return result;
  }

  public String getCheckinOperationName() {
    return SvnBundle.message("checkin.operation.name");
  }

  public List<VcsException> commit(List<Change> changes, String preparedComment) {
    final Collection<FilePath> paths = ChangesUtil.getPaths(changes);
    return commitInt(collectPaths(paths), preparedComment, true, false);
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
    final List<VirtualFile> notUnderWcFiles = new ArrayList<VirtualFile>();
    final SVNWCClient wcClient = mySvnVcs.createWCClient();
    final List<VirtualFile> filteredList = new ArrayList<VirtualFile>();
    for (VirtualFile file : files) {
      if (SvnStatusUtil.fileCanBeAdded(mySvnVcs.getProject(), file)) {
        filteredList.add(file);
      } else {
        notUnderWcFiles.add(file);
      }
    }
    if (! notUnderWcFiles.isEmpty()) {
      final StringBuilder sb = new StringBuilder();
      for (VirtualFile notUnderWcFile : notUnderWcFiles) {
        sb.append(notUnderWcFile.getPath()).append('\n');
      }
      result.add(new VcsException(
          SvnBundle.message("message.Subversion.files.can.not.be.added.to.subversion.are.not.under.working.copy.text",
          sb.toString())));
    }
    final List<SVNException> exceptionList = scheduleUnversionedFilesForAddition(wcClient, filteredList);
    for (SVNException svnException : exceptionList) {
      result.add(new VcsException(svnException));
    }
    return result;
  }

  public static List<SVNException> scheduleUnversionedFilesForAddition(SVNWCClient wcClient, List<VirtualFile> files) {
    List<SVNException> exceptions = new ArrayList<SVNException>();

    Collections.sort(files, FileComparator.getInstance());

    for (VirtualFile file : files) {
      try {
        wcClient.doAdd(new File(FileUtil.toSystemDependentName(file.getPath())), true, false, true, false);
      }
      catch (SVNException e) {
        exceptions.add(e);
      }
    }

    return exceptions;
  }

  private final static class FileComparator implements Comparator<VirtualFile> {
    private static final FileComparator ourInstance = new FileComparator();

    public static FileComparator getInstance() {
      return ourInstance;
    }

    public int compare(final VirtualFile o1, final VirtualFile o2) {
      return o1.getPath().compareTo(o2.getPath());
    }
  }

  public boolean keepChangeListAfterCommit(ChangeList changeList) {
    return false;
  }

  private class KeepLocksComponent implements RefreshableOnComponent {
    private JCheckBox myKeepLocksBox;
    private boolean myIsKeepLocks;
    private JPanel myPanel;

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
