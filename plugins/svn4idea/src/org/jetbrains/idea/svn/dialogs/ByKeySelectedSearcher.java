// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public @Nullable TreeNode getNextSelectedByKey() {
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
