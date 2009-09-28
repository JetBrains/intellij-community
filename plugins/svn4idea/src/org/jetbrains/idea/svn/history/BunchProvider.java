package org.jetbrains.idea.svn.history;

import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;

public interface BunchProvider {
  long getEarliestRevision();

  /**
   * @param desirableSize - not the size that should be returned. it is the size that should be found, and then start or/and end
   * bound revisions might be removed from it.
   * so, if need maximum 50 revisions between 10 and 100, not including 10 and 100 revisions, arguments should be:
   * 100, 10, 52, false, false
   */
  @Nullable
  Fragment getEarliestBunchInInterval(final long earliestRevision, final long oldestRevision, final int desirableSize,
                                      final boolean includeYoungest, final boolean includeOldest) throws SVNException;
  boolean isEmpty();
}
