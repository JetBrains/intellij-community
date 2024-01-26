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
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PyDebugValue;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author amarch
 */

public class PyViewNumericContainerAction extends XDebuggerTreeActionBase {

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    Project p = e.getProject();
    if (p != null && node != null && node.getValueContainer() instanceof PyDebugValue debugValue && node.isComputed()) {
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
    List<XValueNodeImpl> selectedNodes = getSelectedNodes(e.getDataContext());
    if (selectedNodes.size() != 1) {
      e.getPresentation().setVisible(false);
      return;
    }

    XValueNodeImpl node = selectedNodes.get(0);
    if (!(node.getValueContainer() instanceof PyDebugValue debugValue) || !node.isComputed()) {
      e.getPresentation().setVisible(false);
      return;
    }

    String nodeType = debugValue.getType();
    if ("ndarray".equals(nodeType)) {
      e.getPresentation().setText(PyBundle.message("debugger.numeric.view.as.array"));
      e.getPresentation().setVisible(true);
    }
    else if ("DataFrame".equals(nodeType) || "GeoDataFrame".equals(nodeType)) {
      e.getPresentation().setText(PyBundle.message("debugger.numeric.view.as.dataframe"));
      e.getPresentation().setVisible(true);
    }
    else if ("Series".equals(nodeType) || "GeoSeries".equals(nodeType)) {
      e.getPresentation().setText(PyBundle.message("debugger.numeric.view.as.series"));
      e.getPresentation().setVisible(true);
    }
    else {
      e.getPresentation().setVisible(false);
    }
  }
}
