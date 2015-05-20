/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.theoryinpractice.testng.ui;

import com.intellij.execution.testframework.PoolOfTestIcons;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.theoryinpractice.testng.model.TestNGConsoleProperties;
import com.theoryinpractice.testng.model.TestNodeDescriptor;
import com.theoryinpractice.testng.model.TestProxy;
import com.theoryinpractice.testng.model.TreeRootNode;
import org.testng.remote.strprotocol.MessageHelper;
import org.testng.remote.strprotocol.TestResultMessage;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author Hani Suleiman Date: Jul 21, 2005 Time: 11:32:36 PM
 */
public class ResultTreeRenderer extends ColoredTreeCellRenderer
{

  private TestFrameworkRunningModel model;

    public ResultTreeRenderer(TestFrameworkRunningModel model) {
      this.model = model;
    }

    @Override
    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        if (node.getUserObject() instanceof TestNodeDescriptor) {
            TestProxy proxy = ((TestNodeDescriptor) node.getUserObject()).getElement();
            if (node == tree.getModel().getRoot()) {
                TreeRootNode root = (TreeRootNode) proxy;
                if (node.getChildCount() == 0 && !((TestNGResults)model).hasFinishedTests()) {
                    if ((root.isStarted() && root.isInProgress()) || (root.isInProgress() && !root.isStarted())) {
                        setIcon(PoolOfTestIcons.NOT_RAN);
                        append("Instantiating tests... ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    } else if (root.isStarted() && !root.isInProgress()) {
                        setIcon(PoolOfTestIcons.PASSED_ICON);
                        append("All Tests Passed.", SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    } else {
                        setIcon(PoolOfTestIcons.NOT_RAN);
                        append("No Test Results.", SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    }

                } else {
                    setIcon(root.isInProgress() ? TestsProgressAnimator.getCurrentFrame() : getIcon(proxy));
                    append(root.isInProgress() ? "Running tests..." : "Test Results", SimpleTextAttributes.REGULAR_ATTRIBUTES);
                }

                if (model.getProperties().isPaused()) {
                    setIcon(AllIcons.RunConfigurations.TestPaused);
                }
            } else {
                if (proxy.getResultMessage() != null) {
                  final TestResultMessage result = proxy.getResultMessage();
                  final String name = TestProxy.toDisplayText(result, model.getProperties().getProject());
                  append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                } else {
                    append(proxy.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                }
                setIcon(proxy.isInProgress() ? TestsProgressAnimator.getCurrentFrame() : proxy.isInterrupted() ? PoolOfTestIcons.NOT_RAN : getIcon(proxy));
            }
        } else {
            setIcon(TestsProgressAnimator.getCurrentFrame());
            append(node.getUserObject() != null ? node.getUserObject().toString() : "null", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
    }

    private Icon getIcon(TestProxy node) {
        if (node.isResult()) {
            TestResultMessage result = node.getResultMessage();
            switch (result.getResult()) {
                case MessageHelper.PASSED_TEST:
                    if (node.isTearDownFailure()) {
                        return PoolOfTestIcons.TEAR_DOWN_FAILURE;
                    }
                    return PoolOfTestIcons.PASSED_ICON;
                case MessageHelper.SKIPPED_TEST:
                    return PoolOfTestIcons.IGNORED_ICON;
                case MessageHelper.FAILED_TEST:
                    return PoolOfTestIcons.FAILED_ICON;
                case MessageHelper.TEST_STARTED:
                    return PoolOfTestIcons.TERMINATED_ICON;
            }
        } else {
            if (node.getChildCount() == 0) {
              final TestProxy nodeParent = node.getParent();
              if (nodeParent != null && nodeParent.isTearDownFailure()) {
                final TestResultMessage resultMessage = nodeParent.getResultMessage();
                if (resultMessage != null && resultMessage.getResult() == MessageHelper.PASSED_TEST) {
                  return PoolOfTestIcons.FAILED_ICON;
                }
                return PoolOfTestIcons.IGNORED_ICON;
              }
              return PoolOfTestIcons.NOT_RAN;
            }
            boolean hasFail = false;
            boolean hasSkipped = false;
            boolean hasTerminated = false;
            boolean hasTearDownFailure = false;
            for (TestProxy result : node.getChildren()) {
                Icon icon = getIcon(result);
                if (icon == PoolOfTestIcons.FAILED_ICON) {
                    hasFail = true;
                } else if (icon == PoolOfTestIcons.IGNORED_ICON) {
                    hasSkipped = true;
                } else if (icon == PoolOfTestIcons.TERMINATED_ICON) {
                    hasTerminated = true;
                } else if (result.isTearDownFailure()) {
                    hasTearDownFailure = true;
                }
            }
            if (hasTerminated) return PoolOfTestIcons.TERMINATED_ICON;
            if (hasFail) return PoolOfTestIcons.FAILED_ICON;
            if (hasSkipped) return PoolOfTestIcons.IGNORED_ICON;
            if (hasTearDownFailure) return PoolOfTestIcons.TEAR_DOWN_FAILURE;
        }
        return PoolOfTestIcons.PASSED_ICON;
    }
}
