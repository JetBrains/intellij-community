// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.api.EventAction;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.api.ProgressTracker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class IdeaCommitHandler implements CommitEventHandler, ProgressTracker {

  private static final Logger LOG = Logger.getInstance(IdeaCommitHandler.class);

  @Nullable private final ProgressIndicator myProgress;
  @NotNull private final List<VirtualFile> myDeletedFiles = new ArrayList<>();
  private final boolean myCheckCancel;
  private final boolean myTrackDeletedFiles;

  public IdeaCommitHandler(@Nullable ProgressIndicator progress) {
    this(progress, false, false);
  }

  public IdeaCommitHandler(@Nullable ProgressIndicator progress, boolean checkCancel, boolean trackDeletedFiles) {
    myProgress = progress;
    myCheckCancel = checkCancel;
    myTrackDeletedFiles = trackDeletedFiles;
  }

  @NotNull
  public List<VirtualFile> getDeletedFiles() {
    return myDeletedFiles;
  }

  @Override
  public void commitEvent(CommitEventType type, File target) {
    if (myProgress == null) return;
    myProgress.checkCanceled();

    updateProgress(type, target.getPath());
  }

  @Override
  public void committedRevision(long revNum) {
    if (myProgress == null) return;
    myProgress.checkCanceled();
    myProgress.setText2(SvnBundle.message("status.text.committed.revision", revNum));
  }

  @Override
  public void consume(ProgressEvent event) {
    final String path = event.getPath();
    if (path != null) {
      CommitEventType eventType = convert(event.getAction());

      if (CommitEventType.deleting.equals(eventType) && myTrackDeletedFiles) {
        trackDeletedFile(event);
      }
      updateProgress(eventType, path);
    }
  }

  @Override
  public void checkCancelled() throws ProcessCanceledException {
    if (myCheckCancel && myProgress != null) {
      myProgress.checkCanceled();
    }
  }

  private void updateProgress(@NotNull CommitEventType type, @NotNull String target) {
    if (myProgress == null) return;

    if (CommitEventType.adding.equals(type)) {
      myProgress.setText2(SvnBundle.message("progress.text2.adding", target));
    } else if (CommitEventType.deleting.equals(type)) {
      myProgress.setText2(SvnBundle.message("progress.text2.deleting", target));
    } else if (CommitEventType.sending.equals(type)) {
      myProgress.setText2(SvnBundle.message("progress.text2.sending", target));
    } else if (CommitEventType.replacing.equals(type)) {
      myProgress.setText2(SvnBundle.message("progress.text2.replacing", target));
    } else if (CommitEventType.transmittingDeltas.equals(type)) {
      myProgress.setText2(SvnBundle.message("progress.text2.transmitting.delta", target));
    }
  }

  private void trackDeletedFile(@NotNull ProgressEvent event) {
    @NonNls final String filePath = "file://" + event.getFile().getAbsolutePath().replace(File.separatorChar, '/');
    VirtualFile virtualFile =
      ReadAction.compute(() -> VirtualFileManager.getInstance().findFileByUrl(filePath));

    if (virtualFile != null) {
      myDeletedFiles.add(virtualFile);
    }
  }

  @NotNull
  private static CommitEventType convert(@NotNull EventAction action) {
    CommitEventType result = CommitEventType.unknown;

    if (EventAction.COMMIT_ADDED.equals(action)) {
      result = CommitEventType.adding;
    } else if (EventAction.COMMIT_DELETED.equals(action)) {
      result = CommitEventType.deleting;
    } else if (EventAction.COMMIT_MODIFIED.equals(action)) {
      result = CommitEventType.sending;
    } else if (EventAction.COMMIT_REPLACED.equals(action)) {
      result = CommitEventType.replacing;
    } else if (EventAction.COMMIT_DELTA_SENT.equals(action)) {
      result = CommitEventType.transmittingDeltas;
    } else if (EventAction.SKIP.equals(action)) {
      result = CommitEventType.skipped;
    } else if (EventAction.FAILED_OUT_OF_DATE.equals(action)) {
      result = CommitEventType.failedOutOfDate;
    }

    if (CommitEventType.unknown.equals(result)) {
      LOG.warn("Could not create commit event from action " + action);
    }

    return result;
  }
}
