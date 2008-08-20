package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.plugins.ruby.ruby.lang.TextUtil;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitNodeDescriptor;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author: Roman Chernyatchik
 */
public class RTestUnitTestTreeRenderer extends ColoredTreeCellRenderer {
  private TestConsoleProperties myConsoleProperties;

  public RTestUnitTestTreeRenderer(final TestConsoleProperties consoleProperties) {
    myConsoleProperties = consoleProperties;
  }

  public void customizeCellRenderer(final JTree tree,
                                    final Object value,
                                    final boolean selected,
                                    final boolean expanded,
                                    final boolean leaf,
                                    final int row,
                                    final boolean hasFocus) {
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
    final Object userObj = node.getUserObject();
    if (userObj instanceof RTestUnitNodeDescriptor) {
      final RTestUnitNodeDescriptor desc = (RTestUnitNodeDescriptor)userObj;
      final RTestUnitTestProxy testProxy = desc.getElement();

      if (node == tree.getModel().getRoot()) {
        //Root node
        if (node.isLeaf()) {
          TestsPresentationUtil.formatRootNodeWithoutChildren(testProxy, this);
        } else {
          TestsPresentationUtil.formatRootNodeWithChildren(testProxy, this);
        }
      } else {
        TestsPresentationUtil.formatTestProxy(testProxy, this);
      }
      //Done
      return;
    }

    //strange node
    final String text = node.toString();
    //no icon
    append(text != null ? text : TextUtil.EMPTY_STRING, SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }

  public TestConsoleProperties getConsoleProperties() {
    return myConsoleProperties;
  }
}