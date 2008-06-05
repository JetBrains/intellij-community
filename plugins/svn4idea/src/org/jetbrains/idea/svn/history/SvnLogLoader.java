package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.util.List;

public interface SvnLogLoader {
  List<CommittedChangeList> loadInterval(final SVNRevision fromIncluding, final SVNRevision toIncluding,
                                         final int maxCount, final boolean includingYoungest, final boolean includeOldest) throws SVNException;
}
