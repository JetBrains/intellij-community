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

import com.intellij.codeInsight.hint.HintManager;
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
import javax.swing.table.TableCellEditor;
import java.awt.*;
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
  private PagingTableModel myPagingModel;

  private final static int COLUMNS_IN_DEFAULT_SLICE = 40;
  private final static int ROWS_IN_DEFAULT_SLICE = 40;

  private final static int COLUMNS_IN_DEFAULT_VIEW = 1000;
  private final static int ROWS_IN_DEFAULT_VIEW = 1000;

  private final static int HUGE_ARRAY_SIZE = 1024 * 1024;
  private final static String LOAD_SMALLER_SLICE = "Full slice too large and would slow down debugger, shrunk to smaller slice.";

  public NumpyArrayValueProvider(@NotNull XValueNode node, @NotNull PyViewArrayAction.MyDialog dialog, @NotNull Project project) {
    super(node);
    myDialog = dialog;
    myComponent = dialog.getComponent();
    myProject = project;
    myTable = myComponent.getTable();
    myEvaluator = new PyDebuggerEvaluator(project, getValueContainer().getFrameAccessor());
  }

  private PagingTableModel getPagingModel(int[] shape, boolean rendered) {
    assert shape != null;
    final int columns = Math.min(getMaxColumn(shape), COLUMNS_IN_DEFAULT_VIEW);
    if (columns == 0) {
      showError("Slice with zero axis shape.");
    }
    int rows = Math.min(getMaxRow(shape), ROWS_IN_DEFAULT_VIEW);

    return new PagingTableModel(rows, columns, rendered, this) {

      private String myFormat = getFormat();
      private String myBaseSlice = getSliceText();

      @Override
      protected NumpyArraySlice createChunk(int rows, int columns, int rOffset, int cOffset) {
        return new NumpyArraySlice(myBaseSlice, rows, columns, rOffset, cOffset, myFormat, getInstance());
      }

      @Override
      protected Runnable getDataEvaluator(final ComparableArrayChunk chunk) {
        final NumpyArraySlice arraySlice = new NumpyArraySlice(chunk.baseSlice, chunk.rows, chunk.columns, chunk.rOffset, chunk.cOffset,
                                                               myFormat, getInstance());

        if (arraySlice.getFormat().isEmpty()) {
          arraySlice.setFormat(getDefaultFormat());
        }

        return new Runnable() {
          public void run() {
            if (!arraySlice.dataFilled()) {
              arraySlice.fillData(new Runnable() {
                @Override
                public void run() {
                  myLastPresentation = arraySlice;
                  getPendingSet().remove(chunk);
                  notifyNextThread();
                  fireTableCellUpdated(chunk.rOffset, chunk.cOffset);
                  SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                      addDataInCache(arraySlice.rOffset, arraySlice.cOffset, arraySlice.getData());

                      myTable.setDefaultEditor(myTable.getColumnClass(0), getArrayTableCellEditor());
                      myTable.setDefaultRenderer(myTable.getColumnClass(0), myTableCellRenderer);
                    }
                  });
                }
              });
            }
          }
        };
      }
    };
  }

  private void initComponent() {
    //add table renderer
    myTableCellRenderer = new ArrayTableCellRenderer(Double.MIN_VALUE, Double.MIN_VALUE, myDtypeKind);

    myComponent.getScrollPane().getHorizontalScrollBar().addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        clearErrorMessage();
      }

      @Override
      public void focusLost(FocusEvent e) {

      }
    });

    myComponent.getScrollPane().getVerticalScrollBar().addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        clearErrorMessage();
      }

      @Override
      public void focusLost(FocusEvent e) {

      }
    });

    myComponent.getScrollPane().getHorizontalScrollBar().addAdjustmentListener(new AdjustmentListener() {
      @Override
      public void adjustmentValueChanged(AdjustmentEvent e) {
        clearErrorMessage();
      }
    });


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
        doReslice(getSliceText(), null);
      }
    });
  }

  private void initFormatTextFieldAction() {
    myComponent.getFormatTextField().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "strokeEnter");
    myComponent.getFormatTextField().getActionMap().put("strokeEnter", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        doApplyFormat();
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

    DebuggerUIUtil.invokeLater(new Runnable() {
      @Override
      public void run() {
        myComponent.getSliceTextField().setText(getDefaultPresentation());
        myComponent.getFormatTextField().setText(getDefaultFormat());
      }
    });
    startFillTable(new NumpyArraySlice(getDefaultPresentation(), Math.min(getMaxRow(myShape), ROWS_IN_DEFAULT_VIEW),
                                       Math.min(getMaxColumn(myShape), COLUMNS_IN_DEFAULT_VIEW), 0, 0, getDefaultFormat(), getInstance()),
                   false, false);
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

    if (getMaxRow(myShape) * getMaxColumn(myShape) > HUGE_ARRAY_SIZE) {
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

    // fill current slice
    final int columns = Math.min(getMaxColumn(myShape), COLUMNS_IN_DEFAULT_VIEW);
    int rows = Math.min(getMaxRow(myShape), ROWS_IN_DEFAULT_VIEW);
    mySlicePresentation += "[0:" + rows + ", 0:" + columns + "]";

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

  public String getSliceText() {
    if (myComponent.getSliceTextField().getText().isEmpty()) {
      return getDefaultPresentation();
    }
    return myComponent.getSliceTextField().getText();
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
    //todo: see special case with 1D arrays arr[i, :] - row,
    //but arr[:, i] - column with equal shape and ndim
    //http://stackoverflow.com/questions/16837946/numpy-a-2-rows-1-column-file-loadtxt-returns-1row-2-columns
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

  private void startFillTable(final NumpyArraySlice arraySlice, boolean rendered, final boolean inPlace) {
    if (myLastPresentation != null &&
        arraySlice.baseSlice.equals(myLastPresentation.baseSlice) &&
        arraySlice.getFormat().equals(myLastPresentation.getFormat())) {
      return;
    }

    myPagingModel = getPagingModel(myShape, rendered);

    DebuggerUIUtil.invokeLater(new Runnable() {
      @Override
      public void run() {
        myTable.setModel(myPagingModel);
        if (!inPlace) {
          myComponent.getScrollPane().getViewport().setViewPosition(new Point(0, 0));
          RowHeaderTable rowTable = ((ArrayTableForm.JBTableWithRows)myTable).getRowHeaderTable();
          rowTable.setRowShift(0);
        }
        ((PagingTableModel)myTable.getModel()).fireTableDataChanged();
        ((PagingTableModel)myTable.getModel()).fireTableCellUpdated(0, 0);
      }
    });
  }

  private TableCellEditor getArrayTableCellEditor() {
    return new ArrayTableCellEditor(myProject) {

      private String getCellSlice() {
        String expression = getSliceText();
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
                showError(errorMessage);
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

  public void setDtypeKind(String dtype) {
    this.myDtypeKind = dtype;
  }

  public int[] getShape() {
    return myShape;
  }

  public void setShape(int[] shape) {
    this.myShape = shape;
  }

  public void completeCurrentLoad() {
    setBusy(false);
    if (myPagingModel.getColumnCount() < getMaxColumn(myShape) || myPagingModel.getRowCount() < getMaxRow(myShape)) {
      showSliceInfoHint(LOAD_SMALLER_SLICE);
    }
  }

  public void showError(String message) {
    myDialog.setError(message);
    setBusy(false);
  }

  public void showSliceInfoHint(final String message) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        HintManager.getInstance().showInformationHint(myComponent.getSliceTextField().getEditor(), message);
      }
    });
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
        DebuggerUIUtil.invokeLater(new Runnable() {
          @Override
          public void run() {
            myComponent.getFormatTextField().getComponent().setEnabled(false);
          }
        });
        return "%s";
      }
    }
    return "%s";
  }

  public String getFormat() {
    if (myComponent.getFormatTextField().getText().isEmpty()) {
      return getDefaultFormat();
    }
    return myComponent.getFormatTextField().getText();
  }

  private void doReslice(final String newSlice, int[] shape) {
    clearErrorMessage();

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
    startRefillTable(false);
  }

  private void clearErrorMessage() {
    showError(null);
  }

  private static boolean is2DShape(int[] shape) {
    if (shape.length <= 2) {
      return shape.length == 2;
    }
    return false;
  }

  private void doApplyFormat() {
    startRefillTable(true);
  }

  private void startRefillTable(boolean inPlace) {
    clearTableData();
    Rectangle vr = myTable.getVisibleRect();
    int rOffset = myTable.rowAtPoint(vr.getLocation());
    int cOffset = myTable.columnAtPoint(vr.getLocation());
    startFillTable(new NumpyArraySlice(getSliceText(), 0, 0, rOffset, cOffset, getFormat(), this), true, inPlace);
  }

  private void clearTableData() {
    clearErrorMessage();
    ((PagingTableModel)myTable.getModel()).clearCached();
  }

  public String evalTypeFunc(String format) {
    return "\'" + format + "\' % l";
  }

  public int getMaxRow(int[] shape) {
    if (shape != null && shape.length >= 2) {
      return shape[shape.length - 2];
    }
    return 0;
  }

  public int getMaxColumn(int[] shape) {
    if (shape != null && shape.length >= 2) {
      return shape[shape.length - 1];
    }
    return 0;
  }

  public void notifyNextThread() {
    myPagingModel.runNextThread();
  }

  public void setBusy(final boolean busy) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        myComponent.setBusy(busy);
      }
    });
  }
}
