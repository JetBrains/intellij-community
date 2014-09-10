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
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.nodes.RestorableStateNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerEvaluator;
import org.jetbrains.annotations.NotNull;

import javax.naming.directory.InvalidAttributeValueException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.List;

/**
 * @author amarch
 */
class NumpyArrayValueProvider extends ArrayValueProvider {

  private ArrayTableComponent myComponent;
  private JTable myTable;
  private Project myProject;
  private PyDebuggerEvaluator myEvaluator;
  private NumpyArrayPresentation myLastPresentation;

  public NumpyArrayValueProvider(XValueNode node, ArrayTableComponent component, Project project) {
    super(node);
    myComponent = component;
    myProject = project;
    myTable = component.getTable();
    myEvaluator = new PyDebuggerEvaluator(project, ((PyDebugValue)((XValueNodeImpl)node).getValueContainer()).getFrameAccessor());
    myLastPresentation = new NumpyArrayPresentation(((XValueNodeImpl)node).getName());
  }

  private class NumpyArrayPresentation {
    private Object[][] myData;
    private String mySlice;
    private String myArrayName;
    private int[] myShape;
    private int myRows = 0;
    private int myFilledRows = 0;
    private int nextRow = 0;
    private String myDtype;

    public NumpyArrayPresentation(String name) {
      myArrayName = name;
    }

    public NumpyArrayPresentation getInstance() {
      return this;
    }

    public String getName() {
      return myArrayName;
    }

    public int[] getShape() {
      return myShape;
    }

    public void setShape(int[] shape) {
      myShape = shape;
    }

    public String getDtype() {
      return myDtype;
    }

    public void setDtype(String dtype) {
      myDtype = dtype;
    }

    public Object[][] getData() {
      return myData;
    }

    public void fillShape(final boolean stop) {
      XDebuggerEvaluator.XEvaluationCallback callback = new XDebuggerEvaluator.XEvaluationCallback() {
        @Override
        public void evaluated(@NotNull XValue result) {
          try {
            myShape = parseShape(((PyDebugValue)result).getValue());

            if (myShape.length == 1) {
              myShape = new int[]{1, myShape[0]};
            }

            myData = new Object[myShape[myShape.length - 2]][myShape[myShape.length - 1]];
            myRows = myShape[myShape.length - 2];
            if (!stop) {
              startFillTable(getInstance());
            }
          }
          catch (InvalidAttributeValueException e) {
            errorOccurred(e.getMessage());
          }
        }

        @Override
        public void errorOccurred(@NotNull String errorMessage) {
          myComponent.setErrorSpinnerText(errorMessage);
        }
      };
      fillShape(callback);
    }

    public void fillShape(XDebuggerEvaluator.XEvaluationCallback callback) {
      String evalShapeCommand = myArrayName + ".shape";
      myEvaluator.evaluate(evalShapeCommand, callback, null);
    }

    public void fillSliceShape(final XDebuggerEvaluator.XEvaluationCallback callback) {
      if (mySlice == null) {
        callback.errorOccurred("Null slice");
        return;
      }
      XDebuggerEvaluator.XEvaluationCallback innerCallback = new XDebuggerEvaluator.XEvaluationCallback() {
        @Override
        public void evaluated(@NotNull XValue result) {
          try {
            myShape = parseShape(((PyDebugValue)result).getValue());

            if (myShape.length > 2) {
              errorOccurred("Slice not present valid 2d array.");
              return;
            }

            if (myShape.length == 1) {
              myShape = new int[]{1, myShape[0]};
            }
            myData = new Object[myShape[myShape.length - 2]][myShape[myShape.length - 1]];
            myRows = myShape[myShape.length - 2];
            callback.evaluated(result);
          }
          catch (InvalidAttributeValueException e) {
            errorOccurred(e.getMessage());
          }
        }

        @Override
        public void errorOccurred(@NotNull String errorMessage) {
          callback.errorOccurred(errorMessage);
        }
      };

      String evalShapeCommand = mySlice + ".shape";
      myEvaluator.evaluate(evalShapeCommand, innerCallback, null);
    }

    private int[] parseShape(String shape) throws InvalidAttributeValueException {
      String[] dimensions = shape.substring(1, shape.length() - 1).trim().split(",");
      if (dimensions.length > 0) {
        int[] result = new int[dimensions.length];
        for (int i = 0; i < dimensions.length; i++) {
          result[i] = Integer.parseInt(dimensions[i].trim());
        }
        return result;
      }
      else {
        throw new InvalidAttributeValueException("Invalid shape string for " + ((XValueNodeImpl)myBaseNode).getName());
      }
    }

    public void fillType(final boolean stop) {
      XDebuggerEvaluator.XEvaluationCallback callback = new XDebuggerEvaluator.XEvaluationCallback() {
        @Override
        public void evaluated(@NotNull XValue result) {
          myDtype = ((PyDebugValue)result).getValue();
          if (!stop) {
            startFillTable(getInstance());
          }
        }

        @Override
        public void errorOccurred(@NotNull String errorMessage) {

        }
      };
      String evalTypeCommand = myArrayName + ".dtype.kind";
      myEvaluator.evaluate(evalTypeCommand, callback, null);
    }

    public boolean dataFilled() {
      return myRows > 0 && myFilledRows == myRows;
    }

    public void fillData(final boolean stop) {
      final XDebuggerEvaluator.XEvaluationCallback callback = new XDebuggerEvaluator.XEvaluationCallback() {
        @Override
        public void evaluated(@NotNull XValue result) {
          String name = ((PyDebugValue)result).getName();
          XValueNodeImpl node = new XValueNodeImpl(((XValueNodeImpl)myBaseNode).getTree(), null, name, result);
          node.startComputingChildren();
        }

        @Override
        public void errorOccurred(@NotNull String errorMessage) {
        }
      };

      XDebuggerTreeListener treeListener = new XDebuggerTreeListener() {
        @Override
        public void nodeLoaded(@NotNull RestorableStateNode node, String name) {
        }

        @Override
        public void childrenLoaded(@NotNull XDebuggerTreeNode node, @NotNull List<XValueContainerNode<?>> children, boolean last) {
          String fullName = ((XValueNodeImpl)node).getName();
          int row = 0;
          if (fullName != null && fullName.contains("[")) {
            row = Integer.parseInt(fullName.substring(fullName.lastIndexOf('[') + 1, fullName.length() - 2));
          }
          if (myData[row][0] == null) {
            for (int i = 0; i < node.getChildCount() - 1; i++) {
              myData[row][i] = ((XValueNodeImpl)node.getChildAt(i + 1)).getRawValue();
            }
            myFilledRows += 1;
          }
          if (myFilledRows == myRows) {
            node.getTree().removeTreeListener(this);
            if (!stop) {
              startFillTable(getInstance());
            }
          }
          else {
            nextRow += 1;
            startEvalNextRow(callback);
          }
        }
      };

      ((XValueNodeImpl)myBaseNode).getTree().addTreeListener(treeListener);
      nextRow = 0;
      startEvalNextRow(callback);
    }

    private void startEvalNextRow(XDebuggerEvaluator.XEvaluationCallback callback) {
      String evalRowCommand = "list(" + myArrayName;
      if (myShape.length > 2) {
        evalRowCommand += new String(new char[myShape.length - 2]).replace("\0", "[0]");
      }

      if (myShape[0] > 1) {
        evalRowCommand += "[" + nextRow + "])";
      }
      else {
        evalRowCommand += ")";
      }
      myEvaluator.evaluate(evalRowCommand, callback, null);
    }

    public String getSlice() {
      return mySlice;
    }

    public void setSlice(String slice) {
      mySlice = slice;
    }

    public void computeSlice() {
      String presentation = "";

      if (myBaseNode != null) {
        presentation += ((XValueNodeImpl)myBaseNode).getName();

        if (myShape != null) {
          presentation += new String(new char[myShape.length - 2]).replace("\0", "[0]");
          if (myShape[0] == 1) {
            presentation += "[0:" + myShape[1] + "]";
          }
          else {
            presentation += "[0:" + myShape[myShape.length - 2] + "]";
          }
        }
      }
      setSlice(presentation);
    }
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
      presentation = new NumpyArrayPresentation(((XValueNodeImpl)myBaseNode).getName());
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
        return getCellSlice() + " = " + myEditor.getDocument().getText();
      }

      @Override
      public void doOKAction() {

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
                    myEditor.getDocument().setText(corrected);
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
    myComponent.getTextField().setText(myLastPresentation.getSlice());
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
