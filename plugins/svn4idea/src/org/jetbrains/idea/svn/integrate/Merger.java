// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.update.UpdateEventHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Merger implements IMerger {
  protected final List<CommittedChangeList> myChangeLists;
  protected final File myTarget;
  @Nullable protected final ProgressTracker myHandler;
  private final ProgressIndicator myProgressIndicator;
  protected final Url myCurrentBranchUrl;
  private final StringBuilder myCommitMessage;
  protected final SvnConfiguration mySvnConfig;
  private final Project myProject;
  @NotNull protected final SvnVcs myVcs;
  private final String myBranchName;
  private final boolean myRecordOnly;
  private final boolean myInvertRange;
  private final boolean myGroupSequentialChangeLists;

  private MergeChunk myMergeChunk;

  public Merger(final SvnVcs vcs,
                final List<? extends CommittedChangeList> changeLists,
                final File target,
                final UpdateEventHandler handler,
                final Url currentBranchUrl,
                String branchName) {
    this(vcs, changeLists, target, handler, currentBranchUrl, branchName, false, false, false);
  }

  public Merger(@NotNull SvnVcs vcs,
                final List<? extends CommittedChangeList> changeLists,
                final File target,
                final UpdateEventHandler handler,
                final Url currentBranchUrl,
                String branchName,
                boolean recordOnly,
                boolean invertRange,
                boolean groupSequentialChangeLists) {
    myBranchName = branchName;
    myVcs = vcs;
    myProject = vcs.getProject();
    mySvnConfig = vcs.getSvnConfiguration();
    myCurrentBranchUrl = currentBranchUrl;
    myChangeLists = ContainerUtil.sorted(changeLists, ByNumberChangeListComparator.getInstance());
    myTarget = target;
    myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
    myHandler = handler;
    myCommitMessage = new StringBuilder();
    myRecordOnly = recordOnly;
    myInvertRange = invertRange;
    myGroupSequentialChangeLists = groupSequentialChangeLists;
  }

  @Override
  public boolean hasNext() {
    return isInBounds(getNextChunkStart());
  }

  @Override
  public void mergeNext() throws VcsException {
    myMergeChunk = getNextChunk();

    assert myMergeChunk != null;

    setMergeIndicator();
    doMerge();
    appendComment();
  }

  private void setMergeIndicator() {
    if (myProgressIndicator != null) {
      // TODO: Use values from SvnBundle
      myProgressIndicator.setText2("Merging changelist(s) " + myMergeChunk);
    }
  }

  private int getNextChunkStart() {
    return myMergeChunk == null ? 0 : myMergeChunk.nextChunkStart();
  }

  @Nullable
  private MergeChunk getNextChunk() {
    int start = getNextChunkStart();
    int size = 0;

    if (isInBounds(start)) {
      size = myGroupSequentialChangeLists ? getGroupSize(start) : 1;
    }

    return size > 0 ? new MergeChunk(start, size) : null;
  }

  private int getGroupSize(int start) {
    assert isInBounds(start);

    int size = 1;

    while (isInBounds(start + size) && areSequential(listAt(start + size - 1), listAt(start + size))) {
      size++;
    }

    return size;
  }

  private void appendComment() {
    appendComment(myCommitMessage, myBranchName, myMergeChunk.changeLists());
  }

  public static void appendComment(@NotNull StringBuilder builder,
                                   @NotNull String branch,
                                   @NotNull Iterable<? extends CommittedChangeList> changeLists) {
    if (builder.length() == 0) {
      builder.append("Merged from ").append(branch);
    }
    for (CommittedChangeList list : changeLists) {
      builder.append('\n').append(list.getComment().trim()).append(" [from revision ").append(list.getNumber()).append("]");
    }
  }

  protected void doMerge() throws VcsException {
    Target source = Target.on(myCurrentBranchUrl);
    MergeClient client = myVcs.getFactory(myTarget).createMergeClient();

    client.merge(source, myMergeChunk.revisionRange(), myTarget, Depth.INFINITY, mySvnConfig.isMergeDryRun(), myRecordOnly, true,
                 mySvnConfig.getMergeOptions(), myHandler);
  }

  @Override
  @Nullable
  public String getInfo() {
    String result = null;

    if (myMergeChunk != null) {
      // TODO: Use values from SvnBundle
      StringBuilder builder = new StringBuilder("Changelist(s) :");

      for (CommittedChangeList list : myMergeChunk.changeLists()) {
        final String nextComment = list.getComment().trim().replace('\n', '|');
        builder.append("\n").append(list.getNumber()).append(" (").append(nextComment).append(")");
      }
      builder.append(" merging faced problems");
      result = builder.toString();
    }

    return result;
  }

  @Override
  @Nullable
  public String getSkipped() {
    return getSkippedMessage(myMergeChunk != null ? myMergeChunk.chunkAndAfterLists() : ContainerUtil.emptyList());
  }

  @Nullable
  public static String getSkippedMessage(@NotNull List<? extends CommittedChangeList> changeLists) {
    String result = null;

    if (!changeLists.isEmpty()) {
      final StringBuilder sb = new StringBuilder();
      for (int i = 0; i < changeLists.size(); i++) {
        CommittedChangeList list = changeLists.get(i);
        if (i != 0) {
          sb.append(',');
        }
        sb.append(list.getNumber()).append(" (").append(list.getComment().replace('\n', '|')).append(')');
      }

      result = SvnBundle.message("action.Subversion.integrate.changes.warning.skipped.lists.text", sb.toString());
    }

    return result;
  }

  @Override
  public String getComment() {
    return myCommitMessage.toString();
  }

  @Override
  @Nullable
  public File getMergeInfoHolder() {
    return myTarget;
  }

  @Override
  public void afterProcessing() {
    // TODO: Previous logic (previously used GroupMerger) that was applied when grouping was enabled contained its own Topic with no
    // TODO: subscribers - so currently message is sent only when grouping is disabled.
    // TODO: Check if corresponding event is also necessary when grouping is enabled and update subscribers correspondingly.
    if (!myGroupSequentialChangeLists) {
      List<CommittedChangeList> processed =
        myMergeChunk != null
        ? new ArrayList<>(myMergeChunk.chunkAndBeforeLists())
        : ContainerUtil.emptyList();

      BackgroundTaskUtil.syncPublisher(myProject, COMMITTED_CHANGES_MERGED_STATE).event(processed);
    }
  }

  public static final Topic<CommittedChangesMergedStateChanged> COMMITTED_CHANGES_MERGED_STATE =
    new Topic<>("COMMITTED_CHANGES_MERGED_STATE", CommittedChangesMergedStateChanged.class);

  public interface CommittedChangesMergedStateChanged {
    void event(final List<CommittedChangeList> list);
  }

  @NotNull
  private CommittedChangeList listAt(int index) {
    return myChangeLists.get(index);
  }

  private boolean isInBounds(int index) {
    return index >= 0 && index < myChangeLists.size();
  }

  private static boolean areSequential(@NotNull CommittedChangeList list1, @NotNull CommittedChangeList list2) {
    return list1.getNumber() + 1 == list2.getNumber();
  }

  private class MergeChunk {

    private final int myStart;
    private final int mySize;

    MergeChunk(int start, int size) {
      myStart = start;
      mySize = size;
    }

    public int start() {
      return myStart;
    }

    public int end() {
      return myStart + mySize - 1;
    }

    public int size() {
      return mySize;
    }

    public int nextChunkStart() {
      return end() + 1;
    }

    public long lowestNumber() {
      return myChangeLists.get(start()).getNumber();
    }

    public long highestNumber() {
      return myChangeLists.get(end()).getNumber();
    }

    @NotNull
    public List<CommittedChangeList> changeLists() {
      return myChangeLists.subList(start(), nextChunkStart());
    }

    @NotNull
    public List<CommittedChangeList> chunkAndBeforeLists() {
      return myChangeLists.subList(0, nextChunkStart());
    }

    @NotNull
    public List<CommittedChangeList> chunkAndAfterLists() {
      return ContainerUtil.subList(myChangeLists, start());
    }

    @NotNull
    public RevisionRange revisionRange() {
      Revision startRevision = Revision.of(lowestNumber() - 1);
      Revision endRevision = Revision.of(highestNumber());

      return myInvertRange ? new RevisionRange(endRevision, startRevision) : new RevisionRange(startRevision, endRevision);
    }

    @Override
    public String toString() {
      return highestNumber() == lowestNumber() ? String.valueOf(lowestNumber()) : lowestNumber() + "-" + highestNumber();
    }
  }
}
