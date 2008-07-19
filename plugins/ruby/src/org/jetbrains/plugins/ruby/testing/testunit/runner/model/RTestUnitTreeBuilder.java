package org.jetbrains.plugins.ruby.testing.testunit.runner.model;

import com.intellij.execution.testframework.ui.AbstractTestTreeBuilder;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author: Roman Chernyatchik
 */
public class RTestUnitTreeBuilder extends AbstractTestTreeBuilder {
  public RTestUnitTreeBuilder(final JTree tree, final AbstractTreeStructure structure) {
    super(tree,
          new DefaultTreeModel(new DefaultMutableTreeNode(structure.getRootElement())),
                              structure,
                              IndexComparator.INSTANCE);
    initRootNode();
  }

  public void addItem(final RTestUnitTestProxy parentTestProxy, final RTestUnitTestProxy testProxy) {
    parentTestProxy.addChild(testProxy);
    final DefaultMutableTreeNode parentNode = getNodeForElement(parentTestProxy);
    if (parentNode != null) {
      updateSubtree(parentNode);
    }
  }


  protected boolean isAutoExpandNode(final NodeDescriptor nodeDescriptor) {
    //TODO[romeo] move to base class
    return nodeDescriptor.getElement() == getTreeStructure().getRootElement();
  }

  protected boolean isAlwaysShowPlus(final NodeDescriptor descriptor) {
    //TODO[romeo] move to base class
    return false;
  }

  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    //TODO[romeo] maybe move to base class
    return new StatusBarProgress();
  }

  protected boolean isSmartExpand() {
    //TODO[romeo] move to base class
    return false;
  }
}
