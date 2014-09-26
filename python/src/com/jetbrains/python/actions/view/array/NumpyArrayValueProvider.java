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
import com.intellij.openapi.util.Pair;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerEvaluator;
import org.jetbrains.annotations.NotNull;

import javax.management.InvalidAttributeValueException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * @author amarch
 */
class NumpyArrayValueProvider extends ArrayValueProvider {
  private ArrayTableForm myComponent;
  private JBTable myTable;
  private Project myProject;
  private PyDebuggerEvaluator myEvaluator;
  private Numpy2DArraySlice myLastPresentation;
  private String myDtypeKind;
  private int[] myShape;

  private final static int COLUMNS_IN_DEFAULT_SLICE = 50;
  private final static int ROWS_IN_DEFAULT_SLICE = 50;

  public NumpyArrayValueProvider(@NotNull XValueNode node, @NotNull ArrayTableForm component, @NotNull Project project) {
    super(node);
    myComponent = component;
    myProject = project;
    myTable = component.getTable();
    myEvaluator = new PyDebuggerEvaluator(project, ((PyDebugValue)((XValueNodeImpl)node).getValueContainer()).getFrameAccessor());

    initSliceTextFieldAction();
    initFormatTextFieldAction();
  }

  public PyDebugValue getValueContainer() {
    return (PyDebugValue)((XValueNodeImpl)myBaseNode).getValueContainer();
  }

  public PyDebuggerEvaluator getEvaluator() {
    return myEvaluator;
  }

  public void startFillTable() {
    if (myDtypeKind == null) {
      fillType();
      return;
    }

    if (myShape == null) {
      fillShape();
      return;
    }

    List<Pair<Integer, Integer>> defaultSlice = getDefaultSlice();
    //startFillTable(new Numpy2DArraySlice(getNodeName(), defaultSlice, this, getShape(), getDtypeKind(), getDefaultFormat()));
    falseFill();
  }


  private void falseFill() {
    int rsize = 30;
    int csize = 40;
    Object[][] data = new Object[rsize][csize];

    for (int i = 0; i < rsize; i++) {
      for (int j = 0; j < csize; j++) {
        data[i][j] = "(" + i + "," + j + ")";
      }
    }

    final DefaultTableModel model = new MyTableModel(data, range(0, data[0].length - 1));
    DebuggerUIUtil.invokeLater(new Runnable() {
      @Override
      public void run() {
        myTable.setModel(model);
        myTable.setDefaultEditor(myTable.getColumnClass(0), getArrayTableCellEditor());
      }
    });
    myTable.setPaintBusy(false);
  }

  public class MyTableModel extends DefaultTableModel {

    public MyTableModel(Object[][] data, Object[] columnNames) {
      super(data, columnNames);
    }

    public void removeColumn(int column) {
      columnIdentifiers.remove(column);
      for (Object row: dataVector) {
        ((Vector) row).remove(column);
      }
      fireTableStructureChanged();
    }
  }


  private List<Pair<Integer, Integer>> getDefaultSlice() {
    return getSlice(COLUMNS_IN_DEFAULT_SLICE, ROWS_IN_DEFAULT_SLICE);
  }

  private List<Pair<Integer, Integer>> getSlice(int columns, int rows) {
    List<Pair<Integer, Integer>> slices = new ArrayList<Pair<Integer, Integer>>();
    for (int i = 0; i < myShape.length; i++) {
      Pair<Integer, Integer> slice = new Pair<Integer, Integer>(0, 0);
      if (i == myShape.length - 1) {
        slice = new Pair<Integer, Integer>(0, Math.min(myShape[i], columns));
      }
      else if (i == myShape.length - 2) {
        slice = new Pair<Integer, Integer>(0, Math.min(myShape[i], rows));
      }
      slices.add(slice);
    }
    return slices;
  }

  private void fillType() {
    XDebuggerEvaluator.XEvaluationCallback callback = new XDebuggerEvaluator.XEvaluationCallback() {
      @Override
      public void evaluated(@NotNull XValue result) {
        setDtypeKind(((PyDebugValue)result).getValue());
        startFillTable();
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        showError(errorMessage);
      }
    };
    String evalTypeCommand = getNodeName() + ".dtype.kind";
    getEvaluator().evaluate(evalTypeCommand, callback, null);
  }

  private void fillShape() {
    XDebuggerEvaluator.XEvaluationCallback callback = new XDebuggerEvaluator.XEvaluationCallback() {
      @Override
      public void evaluated(@NotNull XValue result) {
        try {
          setShape(parseShape(((PyDebugValue)result).getValue()));
          startFillTable();
        }
        catch (InvalidAttributeValueException e) {
          errorOccurred(e.getMessage());
        }
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        showError(errorMessage);
      }
    };
    String evalShapeCommand = getNodeName() + ".shape";
    getEvaluator().evaluate(evalShapeCommand, callback, null);
  }

  private int[] parseShape(String shape) throws InvalidAttributeValueException {
    String[] dimensions = shape.substring(1, shape.length() - 1).trim().split(",");
    if (dimensions.length > 1) {
      int[] result = new int[dimensions.length];
      for (int i = 0; i < dimensions.length; i++) {
        result[i] = Integer.parseInt(dimensions[i].trim());
      }
      return result;
    }
    else if (dimensions.length == 1) {
      int[] result = new int[2];
      result[0] = 1;
      result[1] = Integer.parseInt(dimensions[0].trim());
      return result;
    }
    else {
      throw new InvalidAttributeValueException("Invalid shape string for " + getNodeName() + ".");
    }
  }

  @Override
  public boolean isNumeric() {
    if (myDtypeKind != null) {
      return "biufc".contains(myDtypeKind.substring(0, 1));
    }
    return false;
  }

  private void startFillTable(final Numpy2DArraySlice arraySlice) {
    if (!arraySlice.dataFilled()) {
      arraySlice.startFillData(new Runnable() {
        @Override
        public void run() {
          Object[][] data = arraySlice.getData();

          if (myLastPresentation == null || !arraySlice.getPresentation().equals(myLastPresentation.getPresentation())) {
            myLastPresentation = arraySlice;
          }

          DefaultTableModel model = new DefaultTableModel(data, range(0, data[0].length - 1));
          myTable.setModel(model);
          myTable.setDefaultEditor(myTable.getColumnClass(0), getArrayTableCellEditor());
          myTable.setPaintBusy(false);

          //enableColor(data);
          myComponent.getSliceTextField().setText(arraySlice.getPresentation());
          myComponent.getFormatTextField().setText(getDefaultFormat());
        }
      });
    }
  }

  private TableCellEditor getArrayTableCellEditor() {
    return new ArrayTableCellEditor(myProject) {

      private String getCellSlice() {
        String expression = myLastPresentation.getPresentation();
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

        if (myEditor.getEditor() == null) {
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
            myComponent.setErrorText(errorMessage);
          }
        }, null);
        super.doOKAction();
      }
    };
  }

  private static String[] range(int min, int max) {
    String[] array = new String[max - min + 1];
    for (int i = min; i <= max; i++) {
      array[i] = Integer.toString(i);
    }
    return array;
  }

  public void setDtypeKind(String dtype) {
    this.myDtypeKind = dtype;
  }

  public String getDtypeKind() {
    return myDtypeKind;
  }

  public int[] getShape() {
    return myShape;
  }

  public void setShape(int[] shape) {
    this.myShape = shape;
  }

  public void showError(String message) {
    myComponent.setErrorText(message);
  }

  public String getDefaultFormat() {
    if (isNumeric()) {
      if (myDtypeKind.equals("f")) {
        return "\'%.5f\'";
      }

      if (myDtypeKind.equals("i") || myDtypeKind.equals("u")) {
        return "\'%d\'";
      }

      if (myDtypeKind.equals("b") || myDtypeKind.equals("c")) {
        myComponent.getFormatTextField().setEditable(false);
        return "\'%s\'";
      }
    }
    return "\'%s\'";
  }

  private void initSliceTextFieldAction() {
    myComponent.getSliceTextField().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "strokeEnter");
    myComponent.getSliceTextField().getActionMap().put("strokeEnter", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        doReslice(myComponent.getSliceTextField().getText());
      }
    });
  }

  private void doReslice(String newSlice) {

  }

  private void initFormatTextFieldAction() {
    myComponent.getFormatTextField().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "strokeEnter");
    myComponent.getFormatTextField().getActionMap().put("strokeEnter", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        doApplyFormat(myComponent.getFormatTextField().getText());
      }
    });
  }

  private void doApplyFormat(String format) {
    myLastPresentation.applyFormat(format, new Runnable() {
      @Override
      public void run() {
        Object[][] data = myLastPresentation.getData();

        DefaultTableModel model = new DefaultTableModel(data, range(0, data[0].length - 1));
        myTable.setModel(model);
        myTable.setDefaultEditor(myTable.getColumnClass(0), getArrayTableCellEditor());

        //enableColor(data);
      }
    });
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
