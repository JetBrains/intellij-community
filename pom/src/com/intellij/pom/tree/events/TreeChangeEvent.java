package com.intellij.pom.tree.events;

import com.intellij.lang.ASTNode;
import com.intellij.pom.event.PomChangeSet;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: Oct 6, 2004
 * Time: 10:56:52 PM
 * To change this template use File | Settings | File Templates.
 */
public interface TreeChangeEvent extends PomChangeSet{
  ASTNode getRootElement();
  ASTNode[] getChangedElements();
  TreeChange getChangesByElement(ASTNode element);

  void addElementaryChange(ASTNode child, ChangeInfo change);
  void clear();
}
