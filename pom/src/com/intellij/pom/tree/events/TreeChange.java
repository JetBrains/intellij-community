package com.intellij.pom.tree.events;

import com.intellij.lang.ASTNode;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: Oct 6, 2004
 * Time: 11:00:28 PM
 * To change this template use File | Settings | File Templates.
 */

public interface TreeChange {
  void addChange(ASTNode child, ChangeInfo changeInfo);

  ASTNode[] getAffectedChildren();
  ChangeInfo getChangeByChild(ASTNode child);

  void composite(TreeChange treeChange);
  boolean isEmpty();

  void removeChange(ASTNode beforeEqualDepth);
}
