package org.jetbrains.idea.svn.integrate;

import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;

public interface MergerFactory {
  Merger createMerger(final SvnVcs vcs, final File target, final UpdateEventHandler handler, final SVNURL currentBranchUrl);
}
