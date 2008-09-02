package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.Consumer;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.integrate.Merger;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.List;

public class RecordOnlyMergerFactory extends ChangeListsMergerFactory {
  private final boolean myUndo;

  public RecordOnlyMergerFactory(final List<CommittedChangeList> changeListsList,
                                 final Consumer<List<CommittedChangeList>> afterProcessing, final boolean isUndo) {
    super(changeListsList, afterProcessing);
    myUndo = isUndo;
  }

  public Merger createMerger(final SvnVcs vcs, final File target, final UpdateEventHandler handler, final SVNURL currentBranchUrl) {
    return new Merger(vcs, myChangeListsList, target, handler, currentBranchUrl, myAfterProcessing) {
      @Override
      protected SVNRevisionRange createRange() {
        if (myUndo) {
            return new SVNRevisionRange(SVNRevision.create(myLatestProcessed.getNumber()), SVNRevision.create(myLatestProcessed.getNumber() - 1));
        }
        return super.createRange();
      }

      @Override
      protected boolean isRecordOnly() {
        return true;
      }
    };
  }
}
