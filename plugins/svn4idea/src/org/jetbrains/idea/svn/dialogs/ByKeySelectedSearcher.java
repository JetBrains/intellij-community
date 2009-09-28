package org.jetbrains.idea.svn.dialogs;

import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.List;

abstract class ByKeySelectedSearcher {
  private final String myKey;
  protected final List<TreeNode> myChildren;

  protected ByKeySelectedSearcher(final String key, final List<TreeNode> myChildren) {
    myKey = key;
    this.myChildren = myChildren;
  }

  protected abstract IterationResultHolder doChecks(final TreeNode node);

  @Nullable
  public TreeNode getNextSelectedByKey() {
    if (myKey != null) {
      for (final TreeNode node : myChildren) {
        final IterationResultHolder checkResult = doChecks(node);
        if (IterationResultHolder.RESULT.equals(checkResult)) {
          return node;
        }
        else if (IterationResultHolder.SKIP.equals(checkResult)) {
          continue;
        }

        if (myKey.compareTo(node.toString()) <= 0) {
          return node;
        }
      }
    }
    return ((myChildren == null) || myChildren.isEmpty()) ? null : myChildren.get(myChildren.size() - 1);
  }
}
