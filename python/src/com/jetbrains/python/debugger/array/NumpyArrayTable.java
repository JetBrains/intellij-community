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
package com.jetbrains.python.debugger.array;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerEvaluator;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyFrameAccessor;
import org.jetbrains.annotations.NotNull;

import javax.management.InvalidAttributeValueException;
import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author amarch
 */
public class NumpyArrayTable {
  private final PyDebugValue myValue;
  private final PyViewArrayAction.ViewArrayDialog myDialog;
  private final ArrayTableForm myComponent;
  private final JTable myTable;
  private Project myProject;
  private PyDebuggerEvaluator myEvaluator;
  private String myDtypeKind;
  private int[] myShape;
  private ArrayTableCellRenderer myTableCellRenderer;
  private AsyncArrayTableModel myPagingModel;

  private final static int COLUMNS_IN_DEFAULT_SLICE = 40;
  private final static int ROWS_IN_DEFAULT_SLICE = 40;

  private final static int COLUMNS_IN_DEFAULT_VIEW = 1000;
  private final static int ROWS_IN_DEFAULT_VIEW = 1000;

  private static final Pattern PY_COMPLEX_NUMBER = Pattern.compile("([+-]?[.\\d^j]*)([+-]?[e.\\d]*j)?");

  private final static int HUGE_ARRAY_SIZE = 1000 * 1000;
  private final static String LOAD_SMALLER_SLICE = "Full slice too large and would slow down debugger, shrunk to smaller slice.";
  private final static String DISABLE_COLOR_FOR_HUGE_ARRAY =
    "Disable color because array too big and calculating min and max would slow down debugging.";

  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.debugger.array.NumpyArrayValueProvider");

  public NumpyArrayTable(@NotNull Project project,
                         @NotNull PyViewArrayAction.ViewArrayDialog dialog, @NotNull PyDebugValue value) {
    myValue = value;
    myDialog = dialog;
    myComponent = new ArrayTableForm(project);
    myTable = myComponent.getTable();
    myProject = project;
    myEvaluator = new PyDebuggerEvaluator(project, getDebugValue().getFrameAccessor());
  }

  public ArrayTableForm getComponent() {
    return myComponent;
  }

  private AsyncArrayTableModel createTableModel(@NotNull int[] shape) {
    final int columns = Math.min(getMaxColumn(shape), COLUMNS_IN_DEFAULT_VIEW);
    int rows = Math.min(getMaxRow(shape), ROWS_IN_DEFAULT_VIEW);
    if (columns == 0 || rows == 0) {
      showError("Slice with zero axis shape.");
    }

    return new AsyncArrayTableModel(rows, columns, this);
  }

  private void initComponent() {
    //add table renderer
    myTableCellRenderer = new ArrayTableCellRenderer(Double.MIN_VALUE, Double.MIN_VALUE, myDtypeKind);

    //add color checkbox listener
    if (!isNumeric()) {
      disableColor();
    }
    else {
      myComponent.getColoredCheckbox().addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          if (e.getSource() == myComponent.getColoredCheckbox()) {

            if (myTable.getRowCount() > 0 &&
                myTable.getColumnCount() > 0 &&
                myTable.getCellRenderer(0, 0) instanceof ArrayTableCellRenderer) {
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
    initSliceFieldActions();

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
    initFormatFieldActions();
  }

  public void disableColor() {
    myTableCellRenderer.setMin(Double.MAX_VALUE);
    myTableCellRenderer.setMax(Double.MIN_VALUE);
    myTableCellRenderer.setColored(false);
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myComponent.getColoredCheckbox().setSelected(false);
        myComponent.getColoredCheckbox().setEnabled(false);
        if (myTable.getColumnCount() > 0) {
          myTable.setDefaultRenderer(myTable.getColumnClass(0), myTableCellRenderer);
        }
      }
    });
  }

  private void initSliceFieldActions() {
    if (myComponent.getSliceTextField().getEditor() == null) {
      LOG.error("Null editor in slice field.");
      return;
    }
    myComponent.getSliceTextField().getEditor().getContentComponent().addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          doReslice(getSliceText(), null);
        }
      }
    });
  }

  private void initFormatFieldActions() {
    if (myComponent.getFormatTextField().getEditor() == null) {
      LOG.error("Null editor in format field.");
      return;
    }
    myComponent.getFormatTextField().getEditor().getContentComponent().addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          doApplyFormat();
        }
      }
    });
  }

  public PyDebugValue getDebugValue() {
    return myValue;
  }

  public PyFrameAccessor getEvaluator() {
    return myValue.getFrameAccessor();
  }

  public void init() {
    Runnable returnToFillTable = new Runnable() {
      @Override
      public void run() {
        init();
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

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myComponent.getSliceTextField().setText(getDefaultPresentation());
        myComponent.getFormatTextField().setText(getDefaultFormat());
        myDialog.setTitle(getTitlePresentation(getDefaultPresentation()));
      }
    });
    initTableModel(false);
  }

  private static String getTitlePresentation(String slice) {
    return "Array View: " + slice;
  }

  private void fillColorRange(@NotNull final Runnable returnToMain) {
    Consumer<PyDebugValue> callback = new Consumer<PyDebugValue>() {
      @Override
      public void consume(@NotNull PyDebugValue result) {
        String rawValue = result.getValue();
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
    };

    if (getMaxRow(myShape) * getMaxColumn(myShape) > HUGE_ARRAY_SIZE) {
      disableColor();
      returnToMain.run();
    }

    String evalTypeCommand = "[" + getNodeName() + ".min(), " + getNodeName() + ".max()]";
    try {
      PyDebugValue value = getEvaluator().evaluate(evalTypeCommand, false, false);
      callback.consume(value);
    }
    catch (PyDebuggerException e) {
      showError(e.getMessage());
    }
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
    if (rows == 1 && columns == 1) {
      return mySlicePresentation;
    }

    if (rows == 1) {
      mySlicePresentation += "[0:" + columns + "]";
    }
    else if (columns == 1) {
      mySlicePresentation += "[0:" + rows + "]";
    }
    else {
      mySlicePresentation += "[0:" + rows + ", 0:" + columns + "]";
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

  public String getSliceText() {
    if (myComponent.getSliceTextField().getText().isEmpty()) {
      return getDefaultPresentation();
    }
    return myComponent.getSliceTextField().getText();
  }

  private void fillType(@NotNull final Runnable returnToMain) {
    String evalTypeCommand = getNodeName() + ".dtype.kind";
    try {
      PyDebugValue value = getEvaluator().evaluate(evalTypeCommand, false, false);
      setDtypeKind(value.getValue());
      returnToMain.run();
    }
    catch (PyDebuggerException e) {
      showError(e.getMessage());
    }
  }

  private void fillShape(@NotNull final Runnable returnToMain) {
    String evalShapeCommand = getEvalShapeCommand(getNodeName());
    try {
      PyDebugValue value = getEvaluator().evaluate(evalShapeCommand, false, false);
      setShape(parseShape(value.getValue()));
      returnToMain.run();
    }
    catch (Exception e) {
      showError(e.getMessage());
    }
  }

  private int[] parseShape(String value) throws InvalidAttributeValueException {
    int index = value.indexOf('#');
    if (index == -1) {
      LOG.error("Wrong shape format: " + value);
      return new int[]{0, 0};
    }
    String shape = value.substring(0, index);
    if (shape.equals("()")) {
      return new int[]{1, 1};
    }

    String[] dimensions = shape.substring(1, shape.length() - 1).trim().split(",");
    if (dimensions.length > 1) {
      int[] result = new int[dimensions.length];
      for (int i = 0; i < dimensions.length; i++) {
        result[i] = Integer.parseInt(dimensions[i].trim());
      }
      return result;
    }
    else if (dimensions.length == 1) {

      //special case with 1D arrays arr[i, :] - row,
      //but arr[:, i] - column with equal shape and ndim
      //http://stackoverflow.com/questions/16837946/numpy-a-2-rows-1-column-file-loadtxt-returns-1row-2-columns
      //explanation: http://stackoverflow.com/questions/15165170/how-do-i-maintain-row-column-orientation-of-vectors-in-numpy?rq=1
      //we use kind of a hack - use information about memory from C_CONTIGUOUS

      boolean isRow = value.substring(value.indexOf("#") + 1).equals("True");
      int[] result = new int[2];
      if (isRow) {
        result[0] = 1;
        result[1] = Integer.parseInt(dimensions[0].trim());
      }
      else {
        result[1] = 1;
        result[0] = Integer.parseInt(dimensions[0].trim());
      }
      return result;
    }
    else {
      throw new InvalidAttributeValueException("Invalid shape string for " + getNodeName() + ".");
    }
  }

  public boolean isNumeric() {
    if (myDtypeKind != null) {
      return "biufc".contains(myDtypeKind.substring(0, 1));
    }
    return false;
  }

  private void initTableModel(final boolean inPlace) {
    myPagingModel = createTableModel(myShape);

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myTable.setModel(myPagingModel);
        if (!inPlace) {
          myComponent.getScrollPane().getViewport().setViewPosition(new Point(0, 0));
          JBTableWithRowHeaders.RowHeaderTable rowTable = ((JBTableWithRowHeaders)myTable).getRowHeaderTable();
          rowTable.setRowShift(0);
        }
        ((AsyncArrayTableModel)myTable.getModel()).fireTableDataChanged();
        ((AsyncArrayTableModel)myTable.getModel()).fireTableCellUpdated(0, 0);
        if (myTable.getColumnCount() > 0) {
          myTable.setDefaultRenderer(myTable.getColumnClass(0), myTableCellRenderer);
        }
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
        else if (myTable.getColumnCount() == 1) {
          expression += "[" + myTable.getSelectedRow() + "]";
        }
        else {
          expression += "[" + myTable.getSelectedRow() + "][" + myTable.getSelectedColumn() + "]";
        }
        if (myTable.getRowCount() == 1 && myTable.getColumnCount() == 1) {
          return getSliceText();
        }
        return expression;
      }

      private String changeValExpression() {
        if (getEditor().getEditor() == null) {
          throw new IllegalStateException("Null editor in table cell.");
        }

        return getCellSlice() + " = " + getEditor().getEditor().getDocument().getText();
      }


      @Override
      public void cancelEditing() {
        super.cancelEditing();
        clearErrorMessage();
      }

      @Override
      public void doOKAction(final int row, final int col) {

        if (getEditor().getEditor() == null) {
          return;
        }

        myEvaluator.evaluate(changeValExpression(), new XDebuggerEvaluator.XEvaluationCallback() {
          @Override
          public void evaluated(@NotNull XValue result) {
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
                  disableColor();
                }

                new WriteCommandAction(null) {
                  protected void run(@NotNull Result result) throws Throwable {
                    if (getEditor().getEditor() != null) {
                      getEditor().getEditor().getDocument().setText(corrected);
                      ((AsyncArrayTableModel)myTable.getModel()).changeValue(row, col, corrected);
                      cancelEditing();
                    }
                  }
                }.execute();
                setLastValue(corrected);
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
        super.doOKAction(row, col);
      }
    };
  }

  public String correctStringValue(@NotNull Object value) {
    if (value instanceof String) {
      String corrected = (String)value;
      if (isNumeric()) {
        if (corrected.startsWith("\'") || corrected.startsWith("\"")) {
          corrected = corrected.substring(1, corrected.length() - 1);
        }
      }
      return corrected;
    }
    else if (value instanceof Integer) {
      return Integer.toString((Integer)value);
    }
    return value.toString();
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
  }

  public void showInfoHint(final String message) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (myComponent.getSliceTextField().getEditor() != null) {
          HintManager.getInstance().showInformationHint(myComponent.getSliceTextField().getEditor(), message);
        }
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
        UIUtil.invokeLaterIfNeeded(new Runnable() {
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
    if (shape == null) {
      String evalShapeCommand = getEvalShapeCommand(newSlice);
      try {
        PyDebugValue result = getEvaluator().evaluate(evalShapeCommand, false, false);
        shape = parseShape(((PyDebugValue)result).getValue());
        if (!is2DShape(shape)) {
          showError("Incorrect slice shape " + ((PyDebugValue)result).getValue() + ".");
        }
        doReslice(newSlice, shape);
      }
      catch (Exception e) {
        showError(e.getMessage());
      }
      return;
    }

    myShape = shape;
    reset();
  }

  private static String getEvalShapeCommand(@NotNull String slice) {
    //add information about memory, see #parseShape comments
    return "repr(" + slice + ".shape)+'#'+repr(" + slice + ".flags['C_CONTIGUOUS'])";
  }

  private void clearErrorMessage() {
    showError(null);
  }

  private static boolean is2DShape(int[] shape) {
    if (shape.length < 2) {
      return false;
    }

    for (int i = 0; i < shape.length - 2; i++) {
      if (shape[i] != 1) {
        return false;
      }
    }

    return true;
  }

  private void doApplyFormat() {
    reset();
  }

  private void reset() {
    clearErrorMessage();
    initTableModel(true);
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

  public void setBusy(final boolean busy) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myComponent.setBusy(busy);
      }
    });
  }

  /**
   * @return double presentation from [0:1] range
   */
  public static double getRangedValue(String value, String type, double min, double max, String complexMax, String complexMin) {
    if ("iuf".contains(type)) {
      return (Double.parseDouble(value) - min) / (max - min);
    }
    else if ("b".equals(type)) {
      return value.equals("True") ? 1 : 0;
    }
    else if ("c".equals(type)) {
      return getComplexRangedValue(value, complexMax, complexMin);
    }
    return 0;
  }

  /**
   * type complex128 in numpy is compared by next rule:
   * A + Bj > C +Dj if A > C or A == C and B > D
   */
  private static double getComplexRangedValue(String value, String complexMax, String complexMin) {
    Pair<Double, Double> med = parsePyComplex(value);
    Pair<Double, Double> max = parsePyComplex(complexMax);
    Pair<Double, Double> min = parsePyComplex(complexMin);
    double range = (med.first - min.first) / (max.first - min.first);
    if (max.first.equals(min.first)) {
      range = (med.second - min.second) / (max.second - min.second);
    }
    return range;
  }

  private static Pair<Double, Double> parsePyComplex(@NotNull String pyComplexValue) {
    if (pyComplexValue.startsWith("(") && pyComplexValue.endsWith(")")) {
      pyComplexValue = pyComplexValue.substring(1, pyComplexValue.length() - 1);
    }
    Matcher matcher = PY_COMPLEX_NUMBER.matcher(pyComplexValue);
    if (matcher.matches()) {
      String real = matcher.group(1);
      String imag = matcher.group(2);
      if (real.contains("j") && imag == null) {
        return new Pair(new Double(0.0), Double.parseDouble(real.substring(0, real.length() - 1)));
      }
      else {
        return new Pair(Double.parseDouble(real), Double.parseDouble(imag.substring(0, imag.length() - 1)));
      }
    }
    else {
      throw new IllegalArgumentException("Not a valid python complex value: " + pyComplexValue);
    }
  }

  public String getNodeName() {
    return (myValue).getName();
  }
}
