package com.intellij.execution.testframework.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.IndexComparator;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * @author: Roman Chernyatchik
 */
public abstract class AbstractTestTreeBuilder extends AbstractTreeBuilder {
  public AbstractTestTreeBuilder(final JTree tree, final DefaultTreeModel defaultTreeModel,
                                 final AbstractTreeStructure structure,
                                 final IndexComparator instance) {
    super(tree, defaultTreeModel, structure, instance);
  }

  public AbstractTestTreeBuilder() {
    super();
  }

  public void repaintWithParents(final AbstractTestProxy testProxy) {
      AbstractTestProxy current = testProxy;
      do {
          DefaultMutableTreeNode node = getNodeForElement(current);
          if (node != null) {
              JTree tree = getTree();
              ((DefaultTreeModel) tree.getModel()).nodeChanged(node);
          }
          current = current.getParent();
      }
      while (current != null);
  }
}
