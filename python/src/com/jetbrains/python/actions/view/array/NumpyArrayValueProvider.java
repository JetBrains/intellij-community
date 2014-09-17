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

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AppUIUtil;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerEvaluator;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * @author amarch
 */
class NumpyArrayValueProvider extends ArrayValueProvider {
  private ArrayTableForm myComponent;
  private JTable myTable;
  private Project myProject;
  private PyDebuggerEvaluator myEvaluator;
  private NumpyArrayPresentation myLastPresentation;

  public NumpyArrayValueProvider(@NotNull XValueNode node, @NotNull ArrayTableForm component, @NotNull Project project) {
    super(node);
    myComponent = component;
    myProject = project;
    myTable = component.getTable();
    myEvaluator = new PyDebuggerEvaluator(project, ((PyDebugValue)((XValueNodeImpl)node).getValueContainer()).getFrameAccessor());
    myLastPresentation = new NumpyArrayPresentation(((XValueNodeImpl)node).getName(), this);
  }

  public ArrayTableForm getComponent(){
    return myComponent;
  }

  public PyDebuggerEvaluator getEvaluator(){
    return myEvaluator;
  }

  public String getNodeName(){
    return ((XValueNodeImpl)myBaseNode).getName();
  }

  public XDebuggerTree getTree(){
    return ((XValueNodeImpl)myBaseNode).getTree();
  }

  @Override
  public boolean isNumeric() {
    if (myLastPresentation.getDtype() != null) {
      return "biufc".contains(myLastPresentation.getDtype().substring(0, 1));
    }
    return false;
  }

  public void startFillTable(NumpyArrayPresentation presentation) {

    if (presentation == null) {
      presentation = new NumpyArrayPresentation(((XValueNodeImpl)myBaseNode).getName(), this);
    }

    if (presentation.getShape() == null) {
      presentation.fillShape(false);
      return;
    }

    if (presentation.getDtype() == null) {
      presentation.fillType(false);
      return;
    }

    if (!presentation.dataFilled()) {
      presentation.fillData(false);
      return;
    }

    if (presentation.getSlice() == null) {
      presentation.computeSlice();
    }

    Object[][] data = presentation.getData();

    if (myLastPresentation == null || !presentation.getSlice().equals(myLastPresentation.getSlice())) {
      myLastPresentation = presentation;
    }

    DefaultTableModel model = new DefaultTableModel(data, range(0, data[0].length - 1));
    myTable.setModel(model);
    myTable.setDefaultEditor(myTable.getColumnClass(0), new ArrayTableCellEditor(myProject) {

      private String getCellSlice() {
        String expression = myLastPresentation.getSlice();
        if (myTable.getRowCount() == 1) {
          expression += "[" + myTable.getSelectedColumn() + "]";
        }
        else {
          expression += "[" + myTable.getSelectedRow() + "][" + myTable.getSelectedColumn() + "]";
        }
        return expression;
      }

      private String changeValExpression() {
        return getCellSlice() + " = " + myEditor.getEditor().getDocument().getText();
      }

      @Override
      public void doOKAction() {

        if (myEditor.getEditor() == null){
          return;
        }

        myEvaluator.evaluate(changeValExpression(), new XDebuggerEvaluator.XEvaluationCallback() {
          @Override
          public void evaluated(@NotNull XValue result) {
            AppUIUtil.invokeOnEdt(new Runnable() {
              @Override
              public void run() {
                XDebuggerTree tree = ((XValueNodeImpl)myBaseNode).getTree();
                final XDebuggerTreeState treeState = XDebuggerTreeState.saveState(tree);
                tree.rebuildAndRestore(treeState);
              }
            });

            XDebuggerEvaluator.XEvaluationCallback callback = new XDebuggerEvaluator.XEvaluationCallback() {
              @Override
              public void evaluated(@NotNull XValue value) {

                //todo: compute presentation and work with
                String text = ((PyDebugValue)value).getValue();
                final String corrected;
                if (!isNumeric()) {
                  if (!text.startsWith("\\\'") && !text.startsWith("\\\"")) {
                    corrected = "\'" + text + "\'";
                  }
                  else {
                    corrected = text;
                  }
                }
                else {
                  corrected = text;
                }

                new WriteCommandAction(null) {
                  protected void run(@NotNull Result result) throws Throwable {
                    if (myEditor.getEditor() != null) {
                      myEditor.getEditor().getDocument().setText(corrected);
                    }
                  }
                }.execute();
                lastValue = corrected;
              }

              @Override
              public void errorOccurred(@NotNull String errorMessage) {
              }
            };

            myEvaluator.evaluate(getCellSlice(), callback, null);
          }

          @Override
          public void errorOccurred(@NotNull String errorMessage) {
            myComponent.setErrorSpinnerText(errorMessage);
          }
        }, null);
        super.doOKAction();
      }
    });
    enableColor(data);
    myComponent.getSliceTextField().setText(myLastPresentation.getSlice());
    myComponent.getFormatTextField().setText(myLastPresentation.getFormat());
  }

  private static String[] range(int min, int max) {
    String[] array = new String[max - min + 1];
    for (int i = min; i <= max; i++) {
      array[i] = Integer.toString(i);
    }
    return array;
  }

  private void enableColor(Object[][] data) {
    if (isNumeric()) {
      double min = Double.MAX_VALUE;
      double max = Double.MIN_VALUE;
      if (data.length > 0) {
        try {
          for (Object[] aData : data) {
            for (int j = 0; j < data[0].length; j++) {
              double d = Double.parseDouble(aData[j].toString());
              min = min > d ? d : min;
              max = max < d ? d : max;
            }
          }
        }
        catch (NumberFormatException e) {
          min = 0;
          max = 0;
        }
      }
      else {
        min = 0;
        max = 0;
      }

      myTable.setDefaultRenderer(myTable.getColumnClass(0), new ArrayTableCellRenderer(min, max));
    }
    else {
      myComponent.getColored().setSelected(false);
      myComponent.getColored().setVisible(false);
    }
  }
}
