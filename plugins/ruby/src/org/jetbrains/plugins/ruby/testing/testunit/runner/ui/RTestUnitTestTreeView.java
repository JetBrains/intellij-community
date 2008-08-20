package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestTreeView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitNodeDescriptor;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

/**
 * @author: Roman Chernyatchik
 */
public class RTestUnitTestTreeView extends TestTreeView {
  protected TreeCellRenderer getRenderer(final TestConsoleProperties properties) {
    return new RTestUnitTestTreeRenderer(properties);
  }

  @Nullable
  public RTestUnitTestProxy getSelectedTest(@NotNull final TreePath selectionPath) {
    final Object lastComponent = selectionPath.getLastPathComponent();
    assert lastComponent != null;

    return getTestProxyFor(lastComponent);
  }

  @Nullable
  public static RTestUnitTestProxy getTestProxyFor(final Object treeNode) {
    final Object userObj = ((DefaultMutableTreeNode)treeNode).getUserObject();
    if (userObj instanceof RTestUnitNodeDescriptor) {
      return ((RTestUnitNodeDescriptor)userObj).getElement();
    }

    return null;
  }
}
