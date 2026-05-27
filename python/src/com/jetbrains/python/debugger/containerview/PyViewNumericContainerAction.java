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

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeBackendOnlyActionBase;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.NodeTypes;
import com.jetbrains.python.debugger.PyDebugValue;
import org.jetbrains.annotations.NotNull;

/**
 * @author amarch
 */

public class PyViewNumericContainerAction extends XDebuggerTreeBackendOnlyActionBase {

  @Override
  protected void perform(@NotNull XValue value, @NlsSafe @NotNull String nodeName, @NotNull AnActionEvent e) {
    Project p = e.getProject();
    if (p != null && value instanceof PyDebugValue debugValue) {
      showNumericViewer(p, debugValue);
    }
  }

  public static void showNumericViewer(Project project, PyDebugValue debugValue) {
    PyDataView.Companion.getInstance(project).show(debugValue);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    XValue value = getSelectedValue(e.getDataContext());
    if (!(value instanceof PyDebugValue debugValue)) {
      e.getPresentation().setVisible(false);
      return;
    }

    String nodeType = debugValue.getType();
    if (NodeTypes.NDARRAY_NODE_TYPE.equals(nodeType) ||
        NodeTypes.RECARRAY_NODE_TYPE.equals(nodeType) ||
        NodeTypes.EAGER_TENSOR_NODE_TYPE.equals(nodeType) ||
        NodeTypes.RESOURCE_VARIABLE_NODE_TYPE.equals(nodeType) ||
        NodeTypes.SPARSE_TENSOR_NODE_TYPE.equals(nodeType) ||
        NodeTypes.TENSOR_NODE_TYPE.equals(nodeType)) {
      e.getPresentation().setText(PyBundle.message("debugger.numeric.view.as.array"));
      e.getPresentation().setVisible(true);
    }
    else if (NodeTypes.DATA_FRAME_NODE_TYPE.equals(nodeType) ||
             NodeTypes.GEO_DATA_FRAME_NODE_TYPE.equals(nodeType) ||
             NodeTypes.DATASET_NODE_TYPE.equals(nodeType)) {
      e.getPresentation().setText(PyBundle.message("debugger.numeric.view.as.dataframe"));
      e.getPresentation().setVisible(true);
    }
    else if (NodeTypes.SERIES_NODE_TYPE.equals(nodeType) || NodeTypes.GEO_SERIES_NODE_TYPE.equals(nodeType)) {
      e.getPresentation().setText(PyBundle.message("debugger.numeric.view.as.series"));
      e.getPresentation().setVisible(true);
    }
    else {
      e.getPresentation().setVisible(false);
    }
  }
}
