// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.*;

public class SvnCheckinEnvironment implements CheckinEnvironment {

  private static final Logger LOG = Logger.getInstance(SvnCheckinEnvironment.class);
  @NotNull private final SvnVcs mySvnVcs;

  public SvnCheckinEnvironment(@NotNull SvnVcs svnVcs) {
    mySvnVcs = svnVcs;
  }

  @NotNull
  @Override
  public RefreshableOnComponent createCommitOptions(@NotNull CheckinProjectPanel commitPanel, @NotNull CommitContext commitContext) {
    return new KeepLocksComponent();
  }

  @Override
  @Nullable
  public String getHelpId() {
    return null;
  }

  private void doCommit(@NotNull Collection<FilePath> committables,
                        String comment,
                        List<VcsException> exception,
                        final Set<String> feedback) {
    MultiMap<Pair<Url, WorkingCopyFormat>, FilePath> map = SvnUtil.splitIntoRepositoriesMap(mySvnVcs, committables, Convertor.self());

    for (Map.Entry<Pair<Url, WorkingCopyFormat>, Collection<FilePath>> entry : map.entrySet()) {
      try {
        doCommitOneRepo(entry.getValue(), comment, exception, feedback, entry.getKey().getSecond());
      }
      catch (VcsException e) {
        LOG.info(e);
        exception.add(e);
      }
    }
  }

  private void doCommitOneRepo(@NotNull Collection<FilePath> committables,
                               String comment,
                               List<VcsException> exception,
                               final Set<String> feedback,
                               @NotNull WorkingCopyFormat format)
  throws VcsException {
    if (committables.isEmpty()) {
      return;
    }

    CommitInfo[] results = mySvnVcs.getFactory(format).createCheckinClient().commit(ChangesUtil.filePathsToFiles(committables), comment);

    final StringBuilder committedRevisions = new StringBuilder();
    for (CommitInfo result : results) {
      if (result != CommitInfo.EMPTY && result.getRevisionNumber() > 0) {
        if (committedRevisions.length() > 0) {
          committedRevisions.append(", ");
        }
        committedRevisions.append(result.getRevisionNumber());
      }
    }
    if (committedRevisions.length() > 0) {
      reportCommittedRevisions(feedback, committedRevisions.toString());
    }
  }

  private void reportCommittedRevisions(Set<String> feedback, String committedRevisions) {
    final Project project = mySvnVcs.getProject();
    final String message = SvnBundle.message("status.text.comitted.revision", committedRevisions);
    if (feedback == null) {
      ApplicationManager.getApplication().invokeLater(() -> new VcsBalloonProblemNotifier(project, message, MessageType.INFO).run(),
                                                      o -> (!project.isOpen()) || project.isDisposed());
    } else {
      feedback.add("Subversion: " + message);
    }
  }

  @NotNull
  private Collection<FilePath> getCommitables(@NotNull List<Change> changes) {
    THashSet<FilePath> result = new THashSet<>(ChangesUtil.CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY);

    ChangesUtil.getPaths(changes.stream()).forEach(path -> {
      if (result.add(path)) {
        addParents(result, path);
      }
    });

    return result;
  }

  private void addParents(@NotNull Collection<FilePath> paths, @NotNull FilePath path) {
    FilePath parent = path;

    while ((parent = parent.getParentPath()) != null && isAddedOrReplaced(parent)) {
      paths.add(parent);
    }
  }

  private boolean isAddedOrReplaced(@NotNull FilePath file) {
    Status status = getStatus(file);

    return status != null && status.is(StatusType.STATUS_ADDED, StatusType.STATUS_REPLACED);
  }

  @Nullable
  private Status getStatus(@NotNull FilePath file) {
    Status result = null;

    try {
      result = mySvnVcs.getFactory(file.getIOFile()).createStatusClient().doStatus(file.getIOFile(), false);
    }
    catch (SvnBindException e) {
      LOG.info(e);
    }

    return result;
  }

  @Override
  public String getCheckinOperationName() {
    return SvnBundle.message("checkin.operation.name");
  }

  @NotNull
  @Override
  public List<VcsException> commit(@NotNull List<Change> changes,
                                   @NotNull String commitMessage,
                                   @NotNull CommitContext commitContext,
                                   @NotNull Set<String> feedback) {
    final List<VcsException> exception = new ArrayList<>();
    final Collection<FilePath> committables = getCommitables(changes);
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();

    if (progress != null) {
      doCommit(committables, commitMessage, exception, feedback);
    }
    else if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(
        () -> doCommit(committables, commitMessage, exception, feedback), SvnBundle.message("progress.title.commit"), false,
        mySvnVcs.getProject());
    }
    else {
      doCommit(committables, commitMessage, exception, feedback);
    }

    return exception;
  }

  @Override
  public List<VcsException> scheduleMissingFileForDeletion(@NotNull List<FilePath> filePaths) {
    List<VcsException> exceptions = new ArrayList<>();
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

  @Override
  public List<VcsException> scheduleUnversionedFilesForAddition(@NotNull List<VirtualFile> files) {
    return scheduleUnversionedFilesForAddition(mySvnVcs, files);
  }

  public static List<VcsException> scheduleUnversionedFilesForAddition(@NotNull SvnVcs vcs, List<? extends VirtualFile> files) {
    return scheduleUnversionedFilesForAddition(vcs, files, false);
  }

  public static List<VcsException> scheduleUnversionedFilesForAddition(@NotNull SvnVcs vcs, List<? extends VirtualFile> files, final boolean recursive) {
    files.sort(FilePathComparator.getInstance());

    ProgressTracker eventHandler = new SvnProgressCanceller() {
      @Override
      public void consume(ProgressEvent event) {
        // TODO: indicator is null here when invoking "Add" action
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        File file = event.getFile();

        if (indicator != null && file != null) {
          indicator.setText(SvnBundle.message("progress.text2.adding", file.getName() + " (" + file.getParent() + ")"));
        }
      }
    };
    List<VcsException> exceptions = new ArrayList<>();
    Depth depth = Depth.allOrEmpty(recursive);

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

  @Override
  public boolean isRefreshAfterCommitNeeded() {
    return true;
  }

  private class KeepLocksComponent implements RefreshableOnComponent {

    @NotNull private final JCheckBox myKeepLocksBox;
    private boolean myIsKeepLocks;
    @NotNull private final JPanel myPanel;
    @NotNull private final JCheckBox myAutoUpdate;

    KeepLocksComponent() {

      myPanel = new JPanel(new BorderLayout());
      myKeepLocksBox = new JCheckBox(SvnBundle.message("checkbox.chckin.keep.files.locked"));
      myKeepLocksBox.setSelected(myIsKeepLocks);
      myAutoUpdate = new JCheckBox("Auto-update after commit");

      myPanel.add(myAutoUpdate, BorderLayout.NORTH);
      myPanel.add(myKeepLocksBox, BorderLayout.CENTER);
    }

    @Override
    public JComponent getComponent() {
      return myPanel;
    }

    public boolean isKeepLocks() {
      return myKeepLocksBox.isSelected();
    }

    public boolean isAutoUpdate() {
      return myAutoUpdate.isSelected();
    }

    @Override
    public void refresh() {
    }

    @Override
    public void saveState() {
      final SvnConfiguration configuration = mySvnVcs.getSvnConfiguration();
      configuration.setKeepLocks(isKeepLocks());
      configuration.setAutoUpdateAfterCommit(isAutoUpdate());
    }

    @Override
    public void restoreState() {
      final SvnConfiguration configuration = mySvnVcs.getSvnConfiguration();
      myIsKeepLocks = configuration.isKeepLocks();
      myAutoUpdate.setSelected(configuration.isAutoUpdateAfterCommit());
    }
  }
}
