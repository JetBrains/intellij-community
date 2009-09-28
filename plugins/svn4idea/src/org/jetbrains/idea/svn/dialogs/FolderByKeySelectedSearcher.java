package org.jetbrains.idea.svn.dialogs;

import javax.swing.tree.TreeNode;
import java.util.List;

class FolderByKeySelectedSearcher extends ByKeySelectedSearcher {
  FolderByKeySelectedSearcher(final String key, final List<TreeNode> myChildren) {
    super(key, myChildren);
  }

  protected IterationResultHolder doChecks(final TreeNode node) {
    if ((! (node instanceof RepositoryTreeNode)) || (node.isLeaf())) {
      return IterationResultHolder.RESULT;
    }
    return IterationResultHolder.DO_NOTHING;
  }
}
