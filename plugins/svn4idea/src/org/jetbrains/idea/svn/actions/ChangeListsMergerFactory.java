package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.integrate.Merger;
import org.jetbrains.idea.svn.integrate.MergerFactory;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChangeListsMergerFactory implements MergerFactory {
  private final List<CommittedChangeList> myChangeListsList;

  public ChangeListsMergerFactory(final List<CommittedChangeList> changeListsList) {
    myChangeListsList = new ArrayList<CommittedChangeList>(changeListsList);
  }

  public Merger createMerger(final SvnVcs vcs, final File target, final UpdateEventHandler handler, final SVNURL currentBranchUrl) {
    return new Merger(vcs, myChangeListsList, target, handler, currentBranchUrl);
  }
}
