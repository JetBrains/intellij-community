package org.jetbrains.idea.svn.dialogs;

import javax.swing.tree.TreeNode;
import java.util.List;

class FileByKeySelectedSearcher extends ByKeySelectedSearcher {
  FileByKeySelectedSearcher(final String key, final List<TreeNode> myChildren) {
    super(key, myChildren);
  }

  protected IterationResultHolder doChecks(final TreeNode node) {
    if (! (node instanceof RepositoryTreeNode)) {
      return IterationResultHolder.RESULT;
    }
    if (! node.isLeaf()) {
      // folder. folders come first
      return IterationResultHolder.SKIP;
    }
    return IterationResultHolder.DO_NOTHING;
  }
}
