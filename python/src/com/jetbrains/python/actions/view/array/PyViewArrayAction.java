/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.actions.view.array;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author amarch
 */

public class PyViewArrayAction extends XDebuggerTreeActionBase {

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    final MyDialog dialog = new MyDialog(e.getProject(), node, nodeName);
    dialog.setTitle("View Array");
    dialog.setValue(node);
    dialog.show();
  }


  private class MyDialog extends DialogWrapper {
    public JTable myTable;
    private XValueNodeImpl myNode;
    private String myNodeName;
    private Project myProject;
    private ArrayTableComponent myComponent;

    private MyDialog(Project project, XValueNodeImpl node, @NotNull String nodeName) {
      super(project, false);
      setModal(false);
      setCancelButtonText("Close");
      setCrossClosesWindow(true);

      myNode = node;
      myNodeName = nodeName;
      myProject = project;

      myComponent = new ArrayTableComponent();
      myTable = myComponent.getTable();

      init();
    }


    public void setValue(XValueNodeImpl node) {
      final ArrayValueProvider valueProvider;

      if (node.getValuePresentation() != null &&
          node.getValuePresentation().getType() != null &&
          node.getValuePresentation().getType().equals("ndarray")) {
        valueProvider = new NumpyArrayValueProvider();

        //myComponent.setDefaultSpinnerText();

        XDebuggerTreeTableListener tableUpdater = new XDebuggerTreeTableListener(node, myTable, myComponent, myProject);

        node.getTree().addTreeListener(tableUpdater);

        node.startComputingChildren();
      }
    }

    private String evaluateFullValue(XValueNodeImpl node) {
      final String[] result = new String[1];

      XFullValueEvaluator.XFullValueEvaluationCallback valueEvaluationCallback = new XFullValueEvaluator.XFullValueEvaluationCallback() {
        @Override
        public void evaluated(@NotNull String fullValue) {
          result[0] = fullValue;
        }

        @Override
        public void evaluated(@NotNull String fullValue, @Nullable Font font) {
          result[0] = fullValue;
        }

        @Override
        public void errorOccurred(@NotNull String errorMessage) {
          result[0] = errorMessage;
        }

        @Override
        public boolean isObsolete() {
          return false;
        }
      };

      if (node.getFullValueEvaluator() != null) {
        node.getFullValueEvaluator().startEvaluation(valueEvaluationCallback);
      }
      else {
        return node.getRawValue();
      }

      return result[0];
    }

    @Override
    @NotNull
    protected Action[] createActions() {
      return new Action[]{getCancelAction()};
    }

    @Override
    protected String getDimensionServiceKey() {
      return "#com.jetbrains.python.actions.view.array.PyViewArrayAction";
    }

    @Override
    protected JComponent createCenterPanel() {
      return myComponent;
    }
  }
}
