package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.execution.testframework.sm.runner.SMTRunnerNodeDescriptor;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

/**
 * @author: Roman Chernyatchik
 */
public class SMTRunnerTestTreeView extends TestTreeView {
  @Nullable private TestResultsViewer myResultsViewer;

  protected TreeCellRenderer getRenderer(final TestConsoleProperties properties) {
    return new TestTreeRenderer(properties);
  }

  @Nullable
  public SMTestProxy getSelectedTest(@NotNull final TreePath selectionPath) {
    final Object lastComponent = selectionPath.getLastPathComponent();
    assert lastComponent != null;

    return getTestProxyFor(lastComponent);
  }

  @Nullable
  public static SMTestProxy getTestProxyFor(final Object treeNode) {
    final Object userObj = ((DefaultMutableTreeNode)treeNode).getUserObject();
    if (userObj instanceof SMTRunnerNodeDescriptor) {
      return ((SMTRunnerNodeDescriptor)userObj).getElement();
    }

    return null;
  }

  public void setTestResultsViewer(final TestResultsViewer resultsViewer) {
    myResultsViewer = resultsViewer;
  }

  @Nullable
  public TestResultsViewer getResultsViewer() {
    return myResultsViewer;
  }
}
