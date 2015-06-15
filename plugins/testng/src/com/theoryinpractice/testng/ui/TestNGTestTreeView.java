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

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestTreeView;
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
    return new ResultTreeRenderer(getTestFrameworkRunningModel());
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