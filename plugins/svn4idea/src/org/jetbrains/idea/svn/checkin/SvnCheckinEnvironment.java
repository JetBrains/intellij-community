// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
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

import java.io.File;
import java.util.List;
import java.util.*;

public final class SvnCheckinEnvironment implements CheckinEnvironment {
  private static final Logger LOG = Logger.getInstance(SvnCheckinEnvironment.class);
  @NotNull private final SvnVcs mySvnVcs;

  public SvnCheckinEnvironment(@NotNull SvnVcs svnVcs) {
    mySvnVcs = svnVcs;
  }

  @NotNull
  @Override
  public RefreshableOnComponent createCommitOptions(@NotNull CheckinProjectPanel commitPanel, @NotNull CommitContext commitContext) {
    return new KeepLocksComponent(mySvnVcs);
  }

  @Override
  @Nullable
  public String getHelpId() {
    return null;
  }

  private void doCommit(@NotNull Collection<? extends FilePath> committables,
                        String comment,
                        List<VcsException> exception,
                        @NotNull Set<? super String> feedback) {
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

  private void doCommitOneRepo(@NotNull Collection<? extends FilePath> committables,
                               String comment,
                               List<VcsException> exception,
                               @NotNull Set<? super String> feedback,
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
      feedback.add(SvnVcs.VCS_DISPLAY_NAME + ": " + SvnBundle.message("status.text.committed.revision", committedRevisions));
    }
  }

  @NotNull
  private Collection<FilePath> getCommitables(@NotNull List<? extends Change> changes) {
    Set<FilePath> result = CollectionFactory.createCustomHashingStrategySet(ChangesUtil.CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY);
    ChangesUtil.getPaths(changes).forEach(path -> {
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
  public List<VcsException> commit(@NotNull List<? extends Change> changes,
                                   @NotNull String commitMessage,
                                   @NotNull CommitContext commitContext,
                                   @NotNull Set<? super String> feedback) {
    final List<VcsException> exception = new ArrayList<>();
    final Collection<FilePath> committables = getCommitables(changes);
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();

    if (progress != null) {
      doCommit(committables, commitMessage, exception, feedback);
    }
    else if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(
        () -> doCommit(committables, commitMessage, exception, feedback),
        SvnBundle.message("progress.title.commit"), false, mySvnVcs.getProject()
      );
    }
    else {
      doCommit(committables, commitMessage, exception, feedback);
    }

    return exception;
  }

  @Override
  public List<VcsException> scheduleMissingFileForDeletion(@NotNull List<? extends FilePath> filePaths) {
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
  public List<VcsException> scheduleUnversionedFilesForAddition(@NotNull List<? extends VirtualFile> files) {
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
}
