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
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.Consumer;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Merger implements IMerger {
  protected final List<CommittedChangeList> myChangeLists;
  protected final File myTarget;
  @Nullable private final ISVNEventHandler myHandler;
  protected int myCount;
  private final ProgressIndicator myProgressIndicator;
  protected CommittedChangeList myLatestProcessed;
  protected final SVNURL myCurrentBranchUrl;
  private final StringBuilder myCommitMessage;
  protected final SvnConfiguration mySvnConfig;
  private final Project myProject;
  @NotNull private final SvnVcs myVcs;
  private final String myBranchName;

  public Merger(final SvnVcs vcs,
                final List<CommittedChangeList> changeLists,
                final File target,
                final UpdateEventHandler handler,
                final SVNURL currentBranchUrl,
                String branchName) {
    myBranchName = branchName;
    myVcs = vcs;
    myProject = vcs.getProject();
    mySvnConfig = SvnConfiguration.getInstance(vcs.getProject());
    myCurrentBranchUrl = currentBranchUrl;
    myChangeLists = changeLists;

    Collections.sort(myChangeLists, ByNumberChangeListComparator.getInstance());

    myTarget = target;
    myCount = 0;
    myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
    myHandler = handler;
    myCommitMessage = new StringBuilder();
  }

  private static class ByNumberChangeListComparator implements Comparator<CommittedChangeList>{
    private final static ByNumberChangeListComparator ourInstance = new ByNumberChangeListComparator();

    public static ByNumberChangeListComparator getInstance() {
      return ourInstance;
    }

    public int compare(final CommittedChangeList o1, final CommittedChangeList o2) {
      return (int) (o1.getNumber() - o2.getNumber());
    }
  }

  public boolean hasNext() {
    return myCount < myChangeLists.size();
  }

  public void mergeNext() throws SVNException, VcsException {
    myLatestProcessed = myChangeLists.get(myCount);
    ++ myCount;

    if (myProgressIndicator != null) {
      myProgressIndicator.setText2(SvnBundle.message("action.Subversion.integrate.changes.progress.integrating.details.text",
                                                     myLatestProcessed.getNumber()));
    }
    doMerge();

    appendComment();
  }

  private void appendComment() {
    if (myCommitMessage.length() == 0) {
      myCommitMessage.append("Merged from ").append(myBranchName);
    }
    final String nextComment = myLatestProcessed.getComment();
    if (nextComment.trim().length() > 0) {
      myCommitMessage.append('\n').append(nextComment).append(" [from revision ").append(myLatestProcessed.getNumber()).append("]");
    }
  }

  protected SVNRevisionRange createRange() {
    return new SVNRevisionRange(SVNRevision.create(myLatestProcessed.getNumber() - 1), SVNRevision.create(myLatestProcessed.getNumber()));
  }

  protected boolean isRecordOnly() {
    return false;
  }

  protected void doMerge() throws SVNException, VcsException {
    SvnTarget source = SvnTarget.fromURL(myCurrentBranchUrl);
    MergeClient client = myVcs.getFactory(myTarget).createMergeClient();

    client.merge(source, createRange(), myTarget, SVNDepth.INFINITY, mySvnConfig.MERGE_DRY_RUN, isRecordOnly(), true,
                 mySvnConfig.getMergeOptions(), myHandler);
  }

  @NonNls
  private List<CommittedChangeList> getTail() {
    return (myCount < myChangeLists.size()) ?
           new ArrayList<CommittedChangeList>(myChangeLists.subList(myCount, myChangeLists.size())) :
           Collections.<CommittedChangeList>emptyList();
  }

  public void getInfo(final Consumer<String> holder, final boolean getLatest) {
    if (getLatest && (myLatestProcessed != null)) {
      holder.consume(SvnBundle.message("action.Subversion.integrate.changes.warning.failed.list.text", myLatestProcessed.getNumber(),
                                   myLatestProcessed.getComment().replace('\n', '|')));
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
    myProject.getMessageBus().syncPublisher(COMMITTED_CHANGES_MERGED_STATE).event(new ArrayList<CommittedChangeList>(myChangeLists.subList(0, myCount)));
  }

  public static final Topic<CommittedChangesMergedStateChanged> COMMITTED_CHANGES_MERGED_STATE =
    new Topic<CommittedChangesMergedStateChanged>("COMMITTED_CHANGES_MERGED_STATE", CommittedChangesMergedStateChanged.class);

  public interface CommittedChangesMergedStateChanged {
    void event(final List<CommittedChangeList> list);
  }
}
