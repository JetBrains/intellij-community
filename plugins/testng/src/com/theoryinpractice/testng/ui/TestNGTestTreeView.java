package com.theoryinpractice.testng.ui;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestTreeView;
import com.theoryinpractice.testng.model.TestNGConsoleProperties;
import com.theoryinpractice.testng.model.TestNodeDescriptor;
import com.theoryinpractice.testng.model.TestProxy;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

/**
 * @author Hani Suleiman Date: Aug 1, 2005 Time: 11:33:12 AM
 */
public class TestNGTestTreeView extends TestTreeView {

  protected TreeCellRenderer getRenderer(final TestConsoleProperties properties) {
    return new ResultTreeRenderer((TestNGConsoleProperties)properties);
  }

  public TestProxy getSelectedTest(@NotNull TreePath treepath) {
    Object lastComponent = treepath.getLastPathComponent();
    return getObject((DefaultMutableTreeNode)lastComponent);
  }

  public static TestProxy getObject(DefaultMutableTreeNode node) {
    if (!(node.getUserObject() instanceof TestNodeDescriptor)) return null;
    return ((TestNodeDescriptor)node.getUserObject()).getElement();
  }

  @Override
  public String convertValueToText(Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    TestProxy proxy = getObject((DefaultMutableTreeNode)value);
    if (proxy != null) {
      return proxy.getName();
    }
    return "";
  }
}