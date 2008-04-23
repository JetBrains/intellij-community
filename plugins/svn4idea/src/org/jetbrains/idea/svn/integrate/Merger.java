package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Merger {
  private final List<CommittedChangeList> myChangeLists;
  protected final File myTarget;

  protected final boolean myDryRun;

  protected final SVNDiffClient myDiffClient;

  protected int myCount;
  private final ProgressIndicator myProgressIndicator;

  protected CommittedChangeList myLatestProcessed;

  protected final SVNURL myCurrentBranchUrl;

  public Merger(final SvnVcs vcs, final List<CommittedChangeList> changeLists, final File target,
                final boolean dryRun, final UpdateEventHandler handler, final SVNURL currentBranchUrl) {
    myCurrentBranchUrl = currentBranchUrl;
    
    myDiffClient = vcs.createDiffClient();

    myChangeLists = changeLists;
    Collections.sort(myChangeLists, ByNumberChangeListComparator.getInstance());

    myTarget = target;
    myDryRun = dryRun;

    myCount = 0;
    myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();

    myDiffClient.setEventHandler(handler);
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

  public void mergeNext() throws SVNException {
    try {
    Thread.sleep(10000);
    } catch (InterruptedException e) {
      
    }
    try {
      doMerge();
    } finally {
      if (myProgressIndicator != null) {
        myProgressIndicator.setFraction((double) (myCount + 1) / (double) myChangeLists.size());
      }
      ++ myCount;
    }
  }

  protected void doMerge() throws SVNException {
    myLatestProcessed = myChangeLists.get(myCount);
    myDiffClient.doMerge(myCurrentBranchUrl, SVNRevision.UNDEFINED, SVNRevision.create(myLatestProcessed.getNumber() - 1),
                         SVNRevision.create(myLatestProcessed.getNumber()), myTarget, true, true, false, myDryRun);
  }

  @NonNls
  private List<CommittedChangeList> getTail() {
    return ((myCount + 1) < myChangeLists.size()) ?
           new ArrayList<CommittedChangeList>(myChangeLists.subList(myCount + 1, myChangeLists.size())) :
           Collections.<CommittedChangeList>emptyList();
  }

  public void addWarnings(final WarningsHolder holder) {
    final List<CommittedChangeList> tail = getTail();

    if (myLatestProcessed != null) {
      holder.addWarning(SvnBundle.message("action.Subversion.integrate.changes.warning.failed.list.text", myLatestProcessed.getNumber()));
    }

    if (! tail.isEmpty()) {
      final StringBuilder sb = new StringBuilder(SvnBundle.message("action.Subversion.integrate.changes.warning.skipped.lists.text"));
      for (int i = 0; i < tail.size(); i++) {
        CommittedChangeList list = tail.get(i);
        if (i != 0) {
          sb.append(',');
        }
        sb.append(list.getNumber());
      }
      holder.addWarning(sb.toString());
    }
  }
}
