/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.treeStructure;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Comparator;

public class SimpleTreeBuilder extends AbstractTreeBuilder {

  public SimpleTreeBuilder(JTree tree, DefaultTreeModel treeModel, AbstractTreeStructure treeStructure, Comparator comparator) {
    super(tree, treeModel, treeStructure, comparator);
  }

  public boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    return ((SimpleNode) nodeDescriptor).isAlwaysShowPlus();
  }

  public boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return ((SimpleNode) nodeDescriptor).isAutoExpandNode();
  }

  public final void updateFromRoot() {
    updateFromRoot(false);
  }

  public void updateFromRoot(boolean rebuild) {
    if (rebuild) {
      cleanUpStructureCaches();
    }

    if (EventQueue.isDispatchThread()) {
      SimpleTreeBuilder.super.updateFromRoot();
    } else {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          SimpleTreeBuilder.super.updateFromRoot();
        }
      });
    }
  }

  protected final DefaultMutableTreeNode createChildNode(final NodeDescriptor childDescr) {
    return new PatchedDefaultMutableTreeNode(childDescr);
  }

  private void cleanUpStructureCaches() {
    if (!(myTreeStructure instanceof SimpleTreeStructure)) return;
    ((SimpleTreeStructure) myTreeStructure).clearCaches();
  }

  public SimpleTreeBuilder initRoot() {
    initRootNode();
    return this;
  }

}
