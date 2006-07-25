/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.treeStructure;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import org.jetbrains.annotations.NotNull;

public abstract class SimpleTreeStructure extends AbstractTreeStructure {

  public Object[] getChildElements(Object element) {
    return ((SimpleNode) element).getChildren();
  }

  public Object getParentElement(Object element) {
    return ((SimpleNode) element).getParent();
  }

  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    return (NodeDescriptor) element;
  }

  public void commit() {
  }

  public boolean hasSomethingToCommit() {
    return false;
  }

  public final void clearCaches() {
    cleanUpCaches((SimpleNode) getRootElement());
  }

  private void cleanUpCaches(SimpleNode node) {
    if (!(node instanceof CachingSimpleNode)) return;

    final CachingSimpleNode cachingNode = ((CachingSimpleNode) node);
    if (cachingNode.getCached() == null) return;

    for (SimpleNode eachChild : cachingNode.myChildren) {
      cleanUpCaches(eachChild);
    }

    cachingNode.cleanUpCache();
  }

}
