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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractFilterChildren;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.commandLine.SvnCommandLineStatusClient;
import org.jetbrains.idea.svn.commandLine.SvnCommitRunner;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

public class SvnCheckinEnvironment implements CheckinEnvironment {

  private static final Logger LOG = Logger.getInstance(SvnCheckinEnvironment.class);
  @NotNull private final SvnVcs mySvnVcs;

  public SvnCheckinEnvironment(@NotNull SvnVcs svnVcs) {
    mySvnVcs = svnVcs;
  }

  public RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel,
                                                             PairConsumer<Object, Object> additionalDataConsumer) {
    return new KeepLocksComponent();
  }

  @Nullable
  public String getDefaultMessageFor(FilePath[] filesToCheckin) {
    return null;
  }

  @Nullable
  public String getHelpId() {
    return null;
  }


  private List<VcsException> commitInt(List<File> paths, final String comment, final boolean force, final Set<String> feedback) {
    final List<VcsException> exception = new ArrayList<VcsException>();
    final List<File> committables = getCommitables(paths);

    final SVNCommitClient committer = mySvnVcs.createCommitClient();

    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    IdeaCommitHandler handler = new IdeaCommitHandler(progress, true, true);
    if (progress != null) {
      committer.setEventHandler(handler);
    }

    if (progress != null) {
      doCommit(committables, committer, comment, force, exception, feedback);
    }
    else if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          doCommit(committables, committer, comment, force, exception, feedback);
        }
      }, SvnBundle.message("progress.title.commit"), false, mySvnVcs.getProject());
    }
    else {
      doCommit(committables, committer, comment, force, exception, feedback);
    }

    // TODO: Check if such processing of deleted files also necessary for command line. And if yes - use one handler instance for both
    // TODO: SVNKit and command line code flows.
    for(VirtualFile f : handler.getDeletedFiles()) {
      f.putUserData(VirtualFile.REQUESTOR_MARKER, this);
    }
    return exception;
  }

  private void doCommit(List<File> committables,
                        SVNCommitClient committer,
                        String comment,
                        boolean force,
                        List<VcsException> exception, final Set<String> feedback) {
    //noinspection unchecked
    final MultiMap<Pair<SVNURL,WorkingCopyFormat>,File> map = SvnUtil.splitIntoRepositoriesMap(mySvnVcs, committables, Convertor.SELF);
    for (Map.Entry<Pair<SVNURL, WorkingCopyFormat>, Collection<File>> entry : map.entrySet()) {
      doCommitOneRepo(entry.getValue(), committer, comment, force, exception, feedback, entry.getKey().getSecond());
    }
  }

  private void doCommitOneRepo(Collection<File> committables,
                               SVNCommitClient committer,
                               String comment,
                               boolean force,
                               List<VcsException> exception, final Set<String> feedback, final WorkingCopyFormat format) {
    if (committables.isEmpty()) {
      return;
    }
    // TODO: Create CommitClient and refactor to use common ClientFactory model.
    if (WorkingCopyFormat.ONE_DOT_EIGHT.equals(format) ||
        !WorkingCopyFormat.ONE_DOT_SIX.equals(format) && mySvnVcs.getSvnConfiguration().isCommandLine()) {
      doWithCommandLine(committables, comment, exception, feedback);
    }
    else {
      doWithSvnkit(committables, committer, comment, force, exception, feedback);
    }
  }

  private void doWithSvnkit(Collection<File> committables,
                            SVNCommitClient committer,
                            String comment,
                            boolean force,
                            List<VcsException> exception, Set<String> feedback) {
    File[] pathsToCommit = committables.toArray(new File[committables.size()]);
    boolean keepLocks = SvnConfiguration.getInstance(mySvnVcs.getProject()).isKeepLocks();
    SVNCommitPacket[] commitPackets = null;
    SVNCommitInfo[] results;
    try {
      commitPackets = committer.doCollectCommitItems(pathsToCommit, keepLocks, force, SVNDepth.EMPTY, true, null);
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
        for (SVNCommitPacket commitPacket : commitPackets) {
          try {
            commitPacket.dispose();
          }
          catch (SVNException e) {
            LOG.info(e);
          }
        }
      }
    }
    final StringBuilder committedRevisions = new StringBuilder();
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
      reportCommittedRevisions(feedback, committedRevisions.toString());
    }
  }

  private void doWithCommandLine(Collection<File> committables, String comment, List<VcsException> exception, Set<String> feedback) {
    // if directory renames were used, IDEA reports all files under them as moved, but for svn we can not pass some of them
    // to commit command - since not all paths are registered as changes -> so we need to filter these cases, but only if
    // there at least some child-parent relationships in passed paths
    try {
      committables = filterCommittables(committables);
    } catch (SVNException e) {
      exception.add(new VcsException(e));
      return;
    }

    IdeaCommitHandler handler = new IdeaCommitHandler(ProgressManager.getInstance().getProgressIndicator());

    try {
      SvnCommitRunner runner = new SvnCommitRunner(mySvnVcs, handler);
      final long revision =
        runner.commit(ArrayUtil.toObjectArray(committables, File.class), comment, SVNDepth.EMPTY, false, false, null, null);

      reportCommittedRevisions(feedback, String.valueOf(revision));
    }
    catch (VcsException e) {
      exception.add(e);
    }
  }

  private Collection<File> filterCommittables(Collection<File> committables) throws SVNException {
    final Set<String> childrenOfSomebody = ContainerUtil.newHashSet();
    new AbstractFilterChildren<File>() {
      @Override
      protected void sortAscending(List<File> list) {
        Collections.sort(list);
      }

      @Override
      protected boolean isAncestor(File parent, File child) {
        // strict here will ensure that for case insensitive file systems paths different only by case will not be treated as ancestors
        // of each other which is what we need to perform renames from one case to another on Windows
        final boolean isAncestor = FileUtil.isAncestor(parent, child, true);
        if (isAncestor) {
          childrenOfSomebody.add(child.getPath());
        }
        return isAncestor;
      }
    }.doFilter(new ArrayList<File>(committables));
    if (! childrenOfSomebody.isEmpty()) {
      List<File> result = ContainerUtil.newArrayList();
      SvnCommandLineStatusClient statusClient = new SvnCommandLineStatusClient(mySvnVcs);

      for (File file : committables) {
        if (!childrenOfSomebody.contains(file.getPath())) {
          result.add(file);
        } else {
          try {
            final SVNStatus status = statusClient.doStatus(file, false);
            if (status != null && ! SVNStatusType.STATUS_NONE.equals(status.getContentsStatus()) &&
                ! SVNStatusType.STATUS_UNVERSIONED.equals(status.getContentsStatus())) {
              result.add(file);
            }
          }
          catch (SVNException e) {
            // not versioned
            LOG.info(e);
            throw e;
          }
        }
      }
      return result;
    }
    return committables;
  }

  private void reportCommittedRevisions(Set<String> feedback, String committedRevisions) {
    final Project project = mySvnVcs.getProject();
    final String message = SvnBundle.message("status.text.comitted.revision", committedRevisions);
    if (feedback == null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
                                                        public void run() {
                                                          new VcsBalloonProblemNotifier(project, message, MessageType.INFO).run();
                                                        }
                                                      }, new Condition<Object>() {
        @Override
        public boolean value(Object o) {
          return (! project.isOpen()) || project.isDisposed();
        }
      });
    } else {
      feedback.add("Subversion: " + message);
    }
  }

  private static class Adder {
    private final List<File> myResult = new ArrayList<File>();
    private final Set<String> myDuplicatesControlSet = new HashSet<String>();

    public void add(final File file) {
      final String path = file.getAbsolutePath();
      if (! myDuplicatesControlSet.contains(path)) {
        myResult.add(file);
        myDuplicatesControlSet.add(path);
      }
    }

    public List<File> getResult() {
      return myResult;
    }
  }

  private List<File> getCommitables(List<File> paths) {
    final Adder adder = new Adder();

    for (File path : paths) {
      File file = path.getAbsoluteFile();
      adder.add(file);
      if (file.getParentFile() != null) {
        addParents(file.getParentFile(), adder);
      }
    }
    return adder.getResult();
  }

  private void addParents(File file, final Adder adder) {
    SVNStatus status = getStatus(file);

    if (status != null &&
        (SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_ADDED) ||
         SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_REPLACED))) {
      // file should be added
      adder.add(file);
      file = file.getParentFile();
      if (file != null) {
        addParents(file, adder);
      }
    }
  }

  @Nullable
  private SVNStatus getStatus(@NotNull File file) {
    SVNStatus result = null;

    try {
      result = mySvnVcs.getFactory(file).createStatusClient().doStatus(file, false);
    }
    catch (SVNException e) {
      LOG.info(e);
    }

    return result;
  }

  private static List<File> collectPaths(@NotNull List<Change> changes) {
    // case sensitive..
    Set<String> paths = ContainerUtil.newHashSet();

    for (Change change : changes) {
      addPath(paths, change.getBeforeRevision());
      addPath(paths, change.getAfterRevision());
    }

    return SvnUtil.toFiles(paths);
  }

  private static void addPath(@NotNull Collection<String> paths, @Nullable ContentRevision revision) {
    if (revision != null) {
      paths.add(revision.getFile().getIOFile().getAbsolutePath());
    }
  }

  public String getCheckinOperationName() {
    return SvnBundle.message("checkin.operation.name");
  }

  public List<VcsException> commit(List<Change> changes,
                                   String preparedComment,
                                   @NotNull NullableFunction<Object, Object> parametersHolder,
                                   Set<String> feedback) {
    return commitInt(collectPaths(changes), preparedComment, true, feedback);
  }

  public List<VcsException> commit(List<Change> changes, String preparedComment) {
    return commit(changes, preparedComment, FunctionUtil.nullConstant(), null);
  }

  public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> filePaths) {
    List<VcsException> exceptions = new ArrayList<VcsException>();
    List<File> files = ChangesUtil.filePathsToFiles(filePaths);

    for (File file : files) {
      try {
        mySvnVcs.getFactory(file).createDeleteClient().delete(file, true, false, null);
      }
      catch (VcsException e) {
        exceptions.add(e);
      }
    }

    return exceptions;
  }

  public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
    return scheduleUnversionedFilesForAddition(mySvnVcs, files);
  }

  public static List<VcsException> scheduleUnversionedFilesForAddition(@NotNull SvnVcs vcs, List<VirtualFile> files) {
    return scheduleUnversionedFilesForAddition(vcs, files, false);
  }

  public static List<VcsException> scheduleUnversionedFilesForAddition(@NotNull SvnVcs vcs, List<VirtualFile> files, final boolean recursive) {
    Collections.sort(files, FilePathComparator.getInstance());

    ISVNEventHandler eventHandler = new SvnProgressCanceller() {
      @Override
      public void handleEvent(SVNEvent event, double progress) throws SVNException {
        // TODO: indicator is null here when invoking "Add" action
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        File file = event.getFile();

        if (indicator != null && file != null) {
          indicator.setText(SvnBundle.message("progress.text2.adding", file.getName() + " (" + file.getParent() + ")"));
        }
      }
    };

    List<VcsException> exceptions = new ArrayList<VcsException>();
    SVNDepth depth = recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY;

    for (VirtualFile file : files) {
      try {
        File convertedFile = VfsUtilCore.virtualToIoFile(file);

        vcs.getFactory(convertedFile).createAddClient().add(convertedFile, depth, true, false, true, eventHandler);
      }
      catch (VcsException e) {
        exceptions.add(e);
      }
    }

    return exceptions;
  }

  public boolean keepChangeListAfterCommit(ChangeList changeList) {
    return false;
  }

  @Override
  public boolean isRefreshAfterCommitNeeded() {
    return true;
  }

  private class KeepLocksComponent implements RefreshableOnComponent {

    @NotNull private final JCheckBox myKeepLocksBox;
    private boolean myIsKeepLocks;
    @NotNull private final JPanel myPanel;
    @NotNull private final JCheckBox myAutoUpdate;

    public KeepLocksComponent() {

      myPanel = new JPanel(new BorderLayout());
      myKeepLocksBox = new JCheckBox(SvnBundle.message("checkbox.chckin.keep.files.locked"));
      myKeepLocksBox.setSelected(myIsKeepLocks);
      myAutoUpdate = new JCheckBox("Auto-update after commit");

      myPanel.add(myAutoUpdate, BorderLayout.NORTH);
      myPanel.add(myKeepLocksBox, BorderLayout.CENTER);
    }

    public JComponent getComponent() {
      return myPanel;
    }

    public boolean isKeepLocks() {
      return myKeepLocksBox.isSelected();
    }

    public boolean isAutoUpdate() {
      return myAutoUpdate.isSelected();
    }

    public void refresh() {
    }

    public void saveState() {
      final SvnConfiguration configuration = mySvnVcs.getSvnConfiguration();
      configuration.setKeepLocks(isKeepLocks());
      configuration.setAutoUpdateAfterCommit(isAutoUpdate());
    }

    public void restoreState() {
      final SvnConfiguration configuration = mySvnVcs.getSvnConfiguration();
      myIsKeepLocks = configuration.isKeepLocks();
      myAutoUpdate.setSelected(configuration.isAutoUpdateAfterCommit());
    }
  }
}
