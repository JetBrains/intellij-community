package org.jetbrains.idea.svn.mergeinfo;

import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;

public interface SelectRootListener {
  void selectionChanged(final WCInfoWithBranches info);
  void force(final WCInfoWithBranches info);
}
