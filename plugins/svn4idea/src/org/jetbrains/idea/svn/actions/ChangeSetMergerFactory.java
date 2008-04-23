package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.integrate.Merger;
import org.jetbrains.idea.svn.integrate.MergerFactory;
import org.jetbrains.idea.svn.integrate.PointMerger;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChangeSetMergerFactory implements MergerFactory {
  private final CommittedChangeList mySelectedList;
  private final List<Change> mySelectedChanges;

  public ChangeSetMergerFactory(final CommittedChangeList selectedList, final List<Change> selectedChanges) {
    mySelectedList = selectedList;
    mySelectedChanges = new ArrayList<Change>(selectedChanges);
  }

  public Merger createMerger(final SvnVcs vcs, final File target, final boolean dryRun, final UpdateEventHandler handler,
                             final SVNURL currentBranchUrl) {
    return new PointMerger(vcs, mySelectedList, target, dryRun, handler, currentBranchUrl, mySelectedChanges);
  }
}
