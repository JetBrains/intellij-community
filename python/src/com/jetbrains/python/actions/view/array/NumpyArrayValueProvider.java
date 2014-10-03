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
import com.intellij.openapi.editor.RangeMarker;
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
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author amarch
 */
class NumpyArrayValueProvider extends ArrayValueProvider {
  private PyViewArrayAction.MyDialog myDialog;
  private ArrayTableForm myComponent;
  private JBTable myTable;
  private Project myProject;
  private PyDebuggerEvaluator myEvaluator;
  private NumpyArraySlice myLastPresentation;
  private String myDtypeKind;
  private int[] myShape;
  private ArrayTableCellRenderer myTableCellRenderer;

  private final static int COLUMNS_IN_DEFAULT_SLICE = 40;
  private final static int ROWS_IN_DEFAULT_SLICE = 40;

  private final static int COLUMNS_IN_DEFAULT_CHUNK = 2;
  private final static int ROWS_IN_DEFAULT_CHUNK = 2;

  private final static int HUGE_ARRAY_SIZE = 1024 * 1024;

  public NumpyArrayValueProvider(@NotNull XValueNode node, @NotNull PyViewArrayAction.MyDialog dialog, @NotNull Project project) {
    super(node);
    myDialog = dialog;
    myComponent = dialog.getComponent();
    myProject = project;
    myTable = myComponent.getTable();
    myEvaluator = new PyDebuggerEvaluator(project, getValueContainer().getFrameAccessor());
  }

  private void initComponent() {

    //add table renderer
    myTableCellRenderer = new ArrayTableCellRenderer(Double.MIN_VALUE, Double.MIN_VALUE, myDtypeKind);

    //add table dynamic scrolling
    FixSizeTableAdjustmentListener tableAdjustmentListener =
      new FixSizeTableAdjustmentListener<NumpyArraySlice>(myTable, getMaxRow(), getMaxColumn(),
                                                          Math.min(getMaxRow(), COLUMNS_IN_DEFAULT_SLICE),
                                                          Math.min(getMaxColumn(), ROWS_IN_DEFAULT_SLICE),
                                                          ROWS_IN_DEFAULT_CHUNK, COLUMNS_IN_DEFAULT_CHUNK) {
        @NotNull
        @Override
        NumpyArraySlice createChunk(String baseSlice, int rows, int columns, int rOffset, int cOffset) {
          return new NumpyArraySlice(baseSlice, rows, columns, rOffset, cOffset, getDefaultFormat(), getInstance());
        }

        @Override
        String getBaseSlice() {
          return NumpyArraySlice.getUpperSlice(myComponent.getSliceTextField().getText(), 1);
        }
      };

    myComponent.getScrollPane().getHorizontalScrollBar().addAdjustmentListener(tableAdjustmentListener);
    myComponent.getScrollPane().getVerticalScrollBar().addAdjustmentListener(tableAdjustmentListener);


    //add color checkbox listener
    if (!isNumeric()) {
      DebuggerUIUtil.invokeLater(new Runnable() {
        @Override
        public void run() {
          myComponent.getColoredCheckbox().setSelected(false);
          myComponent.getColoredCheckbox().setEnabled(false);
        }
      });
    }
    else {
      myComponent.getColoredCheckbox().addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          if (e.getSource() == myComponent.getColoredCheckbox()) {

            if (myTable.getCellRenderer(0, 0) instanceof ArrayTableCellRenderer) {
              ArrayTableCellRenderer renderer = (ArrayTableCellRenderer)myTable.getCellRenderer(0, 0);
              if (myComponent.getColoredCheckbox().isSelected()) {
                renderer.setColored(true);
              }
              else {
                renderer.setColored(false);
              }
            }
            myComponent.getScrollPane().repaint();
          }
        }
      });
    }

    // add slice actions
    initSliceTextFieldAction();
    //make value name read-only
    myComponent.getSliceTextField().addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        myComponent.getSliceTextField().getDocument().createGuardedBlock(0, getNodeName().length());
      }

      @Override
      public void focusLost(FocusEvent e) {
        RangeMarker block = myComponent.getSliceTextField().getDocument().getRangeGuard(0, getNodeName().length());
        if (block != null) {
          myComponent.getSliceTextField().getDocument().removeGuardedBlock(block);
        }
      }
    });

    //add format actions
    initFormatTextFieldAction();
  }

  private void initSliceTextFieldAction() {
    myComponent.getSliceTextField().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "strokeEnter");
    myComponent.getSliceTextField().getActionMap().put("strokeEnter", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        doReslice(myComponent.getSliceTextField().getText(), null);
      }
    });
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

  public NumpyArrayValueProvider getInstance() {
    return this;
  }

  public PyDebugValue getValueContainer() {
    return (PyDebugValue)((XValueNodeImpl)myBaseNode).getValueContainer();
  }

  public PyDebuggerEvaluator getEvaluator() {
    return myEvaluator;
  }

  public void startFillTable() {
    Runnable returnToFillTable = new Runnable() {
      @Override
      public void run() {
        startFillTable();
      }
    };

    if (myDtypeKind == null) {
      fillType(returnToFillTable);
      return;
    }

    if (myShape == null) {
      fillShape(returnToFillTable);
      return;
    }

    if (myTableCellRenderer == null) {
      initComponent();
    }

    if (isNumeric() && myTableCellRenderer.getMax() == Double.MIN_VALUE && myTableCellRenderer.getMin() == Double.MIN_VALUE) {
      fillColorRange(returnToFillTable);
      return;
    }

    int size = myShape.length;
    startFillTable(
      new NumpyArraySlice(getDefaultPresentation(), Math.min(myShape[size - 2], ROWS_IN_DEFAULT_SLICE),
                          Math.min(myShape[size - 1], COLUMNS_IN_DEFAULT_SLICE), 0, 0, getDefaultFormat(), getInstance()));
    //startFillTable(new Numpy2DArraySlice(getNodeName(), defaultSlice, this, getShape(), getDtypeKind(), getDefaultFormat()));
    //falseFill();
  }

  private void fillColorRange(@NotNull final Runnable returnToMain) {
    XDebuggerEvaluator.XEvaluationCallback callback = new XDebuggerEvaluator.XEvaluationCallback() {
      @Override
      public void evaluated(@NotNull XValue result) {
        String rawValue = ((PyDebugValue)result).getValue();
        double min;
        double max;
        String minValue = rawValue.substring(1, rawValue.indexOf(","));
        String maxValue = rawValue.substring(rawValue.indexOf(", ") + 2, rawValue.length() - 1);
        if ("c".equals(myDtypeKind)) {
          min = 0;
          max = 1;
          myTableCellRenderer.setComplexMin(minValue);
          myTableCellRenderer.setComplexMax(maxValue);
        }
        else if ("b".equals(myDtypeKind)) {
          min = minValue.equals("True") ? 1 : 0;
          max = maxValue.equals("True") ? 1 : 0;
        }
        else {
          min = Double.parseDouble(minValue);
          max = Double.parseDouble(maxValue);
        }

        myTableCellRenderer.setMin(min);
        myTableCellRenderer.setMax(max);
        returnToMain.run();
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        showError(errorMessage);
      }
    };

    if (getMaxRow() * getMaxColumn() > HUGE_ARRAY_SIZE) {
      //ndarray too big, calculating min and max would slow down debugging
      myTableCellRenderer.setMin(1.);
      myTableCellRenderer.setMax(1.);
      returnToMain.run();
    }

    String evalTypeCommand = "[" + getNodeName() + ".min(), " + getNodeName() + ".max()]";
    getEvaluator().evaluate(evalTypeCommand, callback, null);
  }

  public String getDefaultPresentation() {
    List<Pair<Integer, Integer>> defaultSlice = getDefaultSlice();
    String mySlicePresentation = getNodeName();
    for (int index = 0; index < defaultSlice.size() - 2; index++) {
      mySlicePresentation += "[" + defaultSlice.get(index).getFirst() + "]";
    }
    return mySlicePresentation;
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

  private void fillType(@NotNull final Runnable returnToMain) {
    XDebuggerEvaluator.XEvaluationCallback callback = new XDebuggerEvaluator.XEvaluationCallback() {
      @Override
      public void evaluated(@NotNull XValue result) {
        setDtypeKind(((PyDebugValue)result).getValue());
        returnToMain.run();
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        showError(errorMessage);
      }
    };
    String evalTypeCommand = getNodeName() + ".dtype.kind";
    getEvaluator().evaluate(evalTypeCommand, callback, null);
  }

  private void fillShape(@NotNull final Runnable returnToMain) {
    XDebuggerEvaluator.XEvaluationCallback callback = new XDebuggerEvaluator.XEvaluationCallback() {
      @Override
      public void evaluated(@NotNull XValue result) {
        try {
          setShape(parseShape(((PyDebugValue)result).getValue()));
          returnToMain.run();
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

  private void startFillTable(final NumpyArraySlice arraySlice) {
    if (myLastPresentation != null && arraySlice.getPresentation().equals(myLastPresentation.getPresentation())) {
      return;
    }
    myLastPresentation = arraySlice;

    DebuggerUIUtil.invokeLater(new Runnable() {
      @Override
      public void run() {
        myTable.setPaintBusy(true);
        myTable.setModel(new DefaultTableModel());
      }
    });

    if (!arraySlice.dataFilled()) {
      arraySlice.startFillData(new Runnable() {
        @Override
        public void run() {
          Object[][] data = arraySlice.getData();

          DefaultTableModel model = new DefaultTableModel(data, range(0, data[0].length - 1));
          myTable.setModel(model);
          myTable.setDefaultEditor(myTable.getColumnClass(0), getArrayTableCellEditor());
          myTable.setDefaultRenderer(myTable.getColumnClass(0), myTableCellRenderer);
          myTable.setPaintBusy(false);

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
        if (myEditor.getEditor() == null) {
          throw new IllegalStateException("Null editor in table cell.");
        }

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
            showError(errorMessage);
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

  public int[] getShape() {
    return myShape;
  }

  public void setShape(int[] shape) {
    this.myShape = shape;
  }

  public void showError(String message) {
    myDialog.setError(message);
    myTable.setPaintBusy(false);
  }

  public String getDefaultFormat() {
    if (isNumeric()) {
      if (myDtypeKind.equals("f")) {
        return "%.5f";
      }

      if (myDtypeKind.equals("i") || myDtypeKind.equals("u")) {
        return "%d";
      }

      if (myDtypeKind.equals("b") || myDtypeKind.equals("c")) {
        myComponent.getFormatTextField().getComponent().setEnabled(false);
        return "%s";
      }
    }
    return "%s";
  }

  private void doReslice(final String newSlice, int[] shape) {
    if (shape == null) {
      XDebuggerEvaluator.XEvaluationCallback callback = new XDebuggerEvaluator.XEvaluationCallback() {
        @Override
        public void evaluated(@NotNull XValue result) {
          try {
            int[] shape = parseShape(((PyDebugValue)result).getValue());
            if (!is2DShape(shape)) {
              errorOccurred("Incorrect slice shape " + ((PyDebugValue)result).getValue() + ".");
            }

            doReslice(newSlice, shape);
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
      String evalShapeCommand = newSlice + ".shape";
      getEvaluator().evaluate(evalShapeCommand, callback, null);
      return;
    }

    myShape = shape;
    int size = myShape.length;
    startFillTable(
      new NumpyArraySlice(newSlice, Math.min(myShape[size - 2], ROWS_IN_DEFAULT_SLICE),
                          Math.min(myShape[size - 1], COLUMNS_IN_DEFAULT_SLICE), 0, 0, myComponent.getFormatTextField().getText(),
                          getInstance()));
  }

  private static boolean is2DShape(int[] shape) {
    if (shape.length <= 2) {
      return shape.length == 2;
    }
    return false;
  }

  private void doApplyFormat(String format) {
    myLastPresentation.applyFormat(format, new Runnable() {
      @Override
      public void run() {
        Object[][] data = myLastPresentation.getData();

        DefaultTableModel model = new DefaultTableModel(data, range(0, data[0].length - 1));
        myTable.setModel(model);
        myTable.setDefaultEditor(myTable.getColumnClass(0), getArrayTableCellEditor());
      }
    });
  }

  public String evalTypeFunc(String format) {
    String typeCommand = "(\'" + format + "\' % l)";
    if (myDtypeKind.equals("f")) {
      typeCommand = "float" + typeCommand;
    }
    else if (myDtypeKind.equals("i") || myDtypeKind.equals("u")) {
      typeCommand = "int" + typeCommand;
    }
    else if (myDtypeKind.equals("b")) {
      typeCommand = "l";
    }
    else if (myDtypeKind.equals("c")) {
      typeCommand = "complex" + typeCommand;
    }
    else {
      typeCommand = "str" + typeCommand;
    }
    return typeCommand;
  }

  public int getMaxRow() {
    if (myShape != null && myShape.length >= 2) {
      return myShape[myShape.length - 2];
    }
    return 0;
  }

  public int getMaxColumn() {
    if (myShape != null && myShape.length >= 2) {
      return myShape[myShape.length - 1];
    }
    return 0;
  }
}
