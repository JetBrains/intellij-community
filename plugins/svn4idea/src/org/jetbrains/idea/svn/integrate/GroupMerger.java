/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.Consumer;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GroupMerger implements IMerger {
  protected final List<CommittedChangeList> myChangeLists;
  protected final File myTarget;
  protected final SVNDiffClient myDiffClient;
  private final ProgressIndicator myProgressIndicator;
  protected final SVNURL myCurrentBranchUrl;
  private final StringBuilder myCommitMessage;
  protected final SvnConfiguration mySvnConfig;
  private final Project myProject;
  private final String myBranchName;
  private final boolean myInverseRange;
  private final boolean myDryRun;
  private final Splitter mySplitter;

  private int myPackStart;
  private int myPackEnd;

  public GroupMerger(final SvnVcs vcs,
                final List<CommittedChangeList> changeLists,
                final File target,
                final UpdateEventHandler handler,
                final SVNURL currentBranchUrl,
                String branchName, boolean inverseRange, boolean dryRun, boolean stepByStep) {
    myBranchName = branchName;
    myInverseRange = inverseRange;
    myDryRun = dryRun;
    myProject = vcs.getProject();
    mySvnConfig = SvnConfiguration.getInstanceChecked(vcs.getProject());
    myCurrentBranchUrl = currentBranchUrl;
    myDiffClient = vcs.createDiffClient();
    myChangeLists = changeLists;

    Collections.sort(myChangeLists, ByNumberChangeListComparator.getInstance());
    mySplitter = stepByStep ? new StepByStepSplitter(myChangeLists) : new GroupSplitter(myChangeLists);

    myTarget = target;
    myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
    myDiffClient.setEventHandler(handler);
    myDiffClient.setMergeOptions(new SVNDiffOptions(mySvnConfig.IGNORE_SPACES_IN_MERGE, mySvnConfig.IGNORE_SPACES_IN_MERGE,
                                                    mySvnConfig.IGNORE_SPACES_IN_MERGE));
    myCommitMessage = new StringBuilder();
    myPackStart = myPackEnd = -1;
  }

  public boolean hasNext() {
    return mySplitter.hasNext();
  }

  public void mergeNext() throws SVNException {
    final int step = mySplitter.step();
    myPackStart = myPackEnd + 1;
    myPackEnd = myPackStart + step - 1;

    if (myProgressIndicator != null) {
      final String numbersBeingMerged;
      if ((myPackStart + 1) == myPackEnd) {
        numbersBeingMerged = "" + myChangeLists.get(myPackStart);
      } else {
        numbersBeingMerged = "" + myChangeLists.get(myPackStart) + "-" + myChangeLists.get(myPackEnd);
      }
      myProgressIndicator.setText2("Merging changelist(s)" + numbersBeingMerged);
    }
    doMerge();

    appendComment();
  }

  private void appendComment() {
    if (myCommitMessage.length() == 0) {
      myCommitMessage.append("Merged from ").append(myBranchName);
    }
    for (int i = myPackStart; i <= myPackEnd; i++) {
      final CommittedChangeList list = myChangeLists.get(i);
      final String nextComment = list.getComment().trim();
      myCommitMessage.append('\n');
      if (nextComment.length() > 0) {
        myCommitMessage.append(nextComment);
      }
      myCommitMessage.append(" [from revision ").append(list.getNumber()).append("]");
    }
  }

  protected SVNRevisionRange createRange() {
    final SVNRevision start = SVNRevision.create(myChangeLists.get(myPackStart).getNumber() - 1);
    final SVNRevision end = SVNRevision.create(myChangeLists.get(myPackEnd).getNumber());
    return myInverseRange ? new SVNRevisionRange(end, start) :  new SVNRevisionRange(start, end);
  }

  protected void doMerge() throws SVNException {
    myDiffClient.doMerge(myCurrentBranchUrl, SVNRevision.UNDEFINED, Collections.singletonList(createRange()),
      myTarget, SVNDepth.INFINITY, true, true, mySvnConfig.MERGE_DRY_RUN, myDryRun);
  }

  @NonNls
  private List<CommittedChangeList> getTail() {
    return (myPackStart < myChangeLists.size()) ?
           new ArrayList<CommittedChangeList>(myChangeLists.subList(myPackStart, myChangeLists.size())) :
           Collections.<CommittedChangeList>emptyList();
  }

  public void getInfo(final Consumer<String> holder, final boolean getLatest) {
    if (getLatest && (myPackStart != -1)) {
      final StringBuilder sb = new StringBuilder("Changelist(s) :");
      for (int i = myPackStart; i <= myPackEnd; i++) {
        final CommittedChangeList list = myChangeLists.get(i);
        final String nextComment = list.getComment().trim().replace('\n', '|');
        sb.append("\n" + list.getNumber()).append(" (").append(nextComment).append(")");
      }
      sb.append(" merging faced problems");
      holder.consume(sb.toString());
    }

    getSkipped(holder);
  }

  public void getSkipped(final Consumer<String> holder) {
    final List<CommittedChangeList> tail = getTail();
    if (! tail.isEmpty()) {
      final StringBuilder sb = new StringBuilder();
      for (int i = 0; i < tail.size(); i++) {
        CommittedChangeList list = tail.get(i);
        if (i != 0) {
          sb.append(',');
        }
        sb.append(list.getNumber()).append(" (").append(list.getComment().replace('\n', '|')).append(')');
      }

      holder.consume(SvnBundle.message("action.Subversion.integrate.changes.warning.skipped.lists.text", sb.toString()));
    }
  }

  public String getComment() {
    return myCommitMessage.toString();
  }

  @Nullable
  public File getMergeInfoHolder() {
    return myTarget;
  }

  public void afterProcessing() {
    // bounds approximately ok
    myProject.getMessageBus().syncPublisher(COMMITTED_CHANGES_MERGED_STATE).event(new ArrayList<CommittedChangeList>(
      myChangeLists.subList(0, myPackEnd > 0 ? myPackEnd : myChangeLists.size())));
  }

  public static final Topic<CommittedChangesMergedStateChanged> COMMITTED_CHANGES_MERGED_STATE =
    new Topic<CommittedChangesMergedStateChanged>("COMMITTED_CHANGES_MERGED_STATE", CommittedChangesMergedStateChanged.class);

  public interface CommittedChangesMergedStateChanged {
    void event(final List<CommittedChangeList> list);
  }

  private static class StepByStepSplitter extends Splitter {
    private final int myTotal;
    private int myCnt;

    private StepByStepSplitter(final List<CommittedChangeList> lists) {
      super(lists);
      myTotal = lists.size();
      myCnt = 0;
    }

    @Override
    public int step() {
      ++ myCnt;
      return 1;
    }

    @Override
    public boolean hasNext() {
      return myCnt < myTotal;
    }
  }
}
