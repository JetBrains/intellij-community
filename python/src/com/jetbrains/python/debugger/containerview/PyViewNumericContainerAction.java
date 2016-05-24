/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.debugger.containerview;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.array.NumpyArrayTable;
import com.jetbrains.python.debugger.dataframe.DataFrameTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;

/**
 * @author amarch
 */

public class PyViewNumericContainerAction extends XDebuggerTreeActionBase {

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    Project p = e.getProject();
    if (p != null && node != null && node.getValueContainer() instanceof PyDebugValue && node.isComputed()) {
      PyDebugValue debugValue = (PyDebugValue)node.getValueContainer();
      String nodeType = debugValue.getType();
      final ViewNumericContainerDialog dialog;
      if ("ndarray".equals(nodeType)) {
        dialog = new ViewNumericContainerDialog(p, (dialogWrapper) -> {
          NumpyArrayTable arrayTable = new NumpyArrayTable(p, dialogWrapper, debugValue);
          arrayTable.init();
          return arrayTable.getComponent().getMainPanel();
        });
      }
      else if (("DataFrame".equals(nodeType))) {
        dialog = new ViewNumericContainerDialog(p, (dialogWrapper) -> {
          DataFrameTable dataFrameTable = new DataFrameTable(p, dialogWrapper, debugValue);
          dataFrameTable.init();
          return dataFrameTable.getComponent().getMainPanel();
        });
      }
      else {
        throw new IllegalStateException("Cannot render node type: " + nodeType);
      }

      dialog.show();
    }
  }

  @Nullable
  private static TreePath[] getSelectedPaths(DataContext dataContext) {
    XDebuggerTree tree = XDebuggerTree.getTree(dataContext);
    return tree == null ? null : tree.getSelectionPaths();
  }

  @Override
  public void update(AnActionEvent e) {
    TreePath[] paths = getSelectedPaths(e.getDataContext());
    if (paths != null) {
      if (paths.length > 1) {
        e.getPresentation().setVisible(false);
        return;
      }

      XValueNodeImpl node = getSelectedNode(e.getDataContext());
      if (node != null && node.getValueContainer() instanceof PyDebugValue && node.isComputed()) {
        PyDebugValue debugValue = (PyDebugValue)node.getValueContainer();

        String nodeType = debugValue.getType();
        if ("ndarray".equals(nodeType)) {
          e.getPresentation().setText("View as Array");
          e.getPresentation().setVisible(true);
        }
        else if (("DataFrame".equals(nodeType))) {
          e.getPresentation().setText("View as DataFrame");
          e.getPresentation().setVisible(true);
        }
        else {
          e.getPresentation().setVisible(false);
        }
      }
      else
      {
        e.getPresentation().setVisible(false);
      }
    }
  }
}
