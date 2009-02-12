package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.execution.testframework.sm.runner.SMTRunnerNodeDescriptor;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.actionSystem.DataKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

/**
 * @author: Roman Chernyatchik
 */
public class SMTRunnerTestTreeView extends TestTreeView {
  public static final DataKey<SMTRunnerTestTreeView> SM_TEST_RUNNER_VIEW  = DataKey.create("SM_TEST_RUNNER_VIEW");

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

  @Override
  public Object getData(final String dataId) {
    if (dataId.equals(SM_TEST_RUNNER_VIEW.getName())) {
      return this;
    }
    return super.getData(dataId);
  }
}
