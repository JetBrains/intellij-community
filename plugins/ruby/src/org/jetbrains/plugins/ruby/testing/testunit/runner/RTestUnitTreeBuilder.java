package org.jetbrains.plugins.ruby.testing.testunit.runner;

import com.intellij.execution.testframework.ui.AbstractTestTreeBuilder;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * @author: Roman Chernyatchik
 */
public class RTestUnitTreeBuilder extends AbstractTestTreeBuilder {
  public RTestUnitTreeBuilder(final JTree tree, final RTestUnitTreeStructure structure) {
    super(tree,
          new DefaultTreeModel(new DefaultMutableTreeNode(structure.getRootElement())),
          structure,
          IndexComparator.INSTANCE);
    
    initRootNode();
  }

  public RTestUnitTreeStructure getRTestUnitTreeStructure() {
    return ((RTestUnitTreeStructure)getTreeStructure()) ;
  }

  public void updateTestsSubtree(final RTestUnitTestProxy parentTestProxy) {
    final AbstractTreeUpdater updater = getUpdater();
    if (updater != null) {
      updater.addSubtreeToUpdateByElement(parentTestProxy);
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

  /**
   * for java unit tests
   */
  public void performUpdate() {
    getUpdater().performUpdate();
  }
}
