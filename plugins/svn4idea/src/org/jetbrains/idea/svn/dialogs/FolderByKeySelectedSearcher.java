// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import javax.swing.tree.TreeNode;
import java.util.List;

class FolderByKeySelectedSearcher extends ByKeySelectedSearcher {
  FolderByKeySelectedSearcher(final String key, final List<TreeNode> myChildren) {
    super(key, myChildren);
  }

  @Override
  protected IterationResultHolder doChecks(final TreeNode node) {
    if ((! (node instanceof RepositoryTreeNode)) || (node.isLeaf())) {
      return IterationResultHolder.RESULT;
    }
    return IterationResultHolder.DO_NOTHING;
  }
}
