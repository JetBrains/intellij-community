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

import com.google.common.util.concurrent.ListenableFutureTask;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.ApplicationManager;
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
import com.jetbrains.python.debugger.*;
import org.jetbrains.annotations.NotNull;

import javax.management.InvalidAttributeValueException;
import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    myComponent = new ArrayTableForm(project, new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          doReslice();
        }
      }
    }, new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          doApplyFormat();
        }
      }
    });
    myTable = myComponent.getTable();
    myProject = project;
    myEvaluator = new PyDebuggerEvaluator(project, getDebugValue().getFrameAccessor());
  }

  public ArrayTableForm getComponent() {
    return myComponent;
  }

  private void initComponent() {
    myComponent.getColoredCheckbox().setEnabled(false);
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

    //make value name read-only
    myComponent.getSliceTextField().addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        myComponent.getSliceTextField().getDocument().createGuardedBlock(0, getNodeFullName().length());
      }

      @Override
      public void focusLost(FocusEvent e) {
        RangeMarker block = myComponent.getSliceTextField().getDocument().getRangeGuard(0, getNodeFullName().length());
        if (block != null) {
          myComponent.getSliceTextField().getDocument().removeGuardedBlock(block);
        }
      }
    });
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

  public PyDebugValue getDebugValue() {
    return myValue;
  }

  public PyFrameAccessor getEvaluator() {
    return myValue.getFrameAccessor();
  }

  public void init() {
    init(getDebugValue().getEvaluationExpression(), false);
  }

  public void init(final String slice, final boolean inPlace) {
    initComponent();

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final PyDebugValue value = getDebugValue();
        PyDebugValue parent = value.getParent();
        final PyDebugValue slicedValue =
          new PyDebugValue(slice, value.getType(), value.getValue(), value.isContainer(), value.isErrorOnEval(),
                           parent, value.getFrameAccessor());

        final String format = getFormat().isEmpty() ? "%" : getFormat();

        try {
          initUi(value.getFrameAccessor()
                   .getArrayItems(slicedValue, 0, 0, -1, -1, format), inPlace);
        }
        catch (PyDebuggerException e) {
          showError(e.getMessage());
        }
      }
    });
  }

  private void initUi(@NotNull final ArrayChunk chunk, final boolean inPlace) {
      myPagingModel = new AsyncArrayTableModel(Math.min(chunk.getRows(), ROWS_IN_DEFAULT_VIEW),
                                               Math.min(chunk.getColumns(), COLUMNS_IN_DEFAULT_VIEW), this);
      myPagingModel.addToCache(chunk);
      myDtypeKind = chunk.getType();

      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          myTable.setModel(myPagingModel);
          myComponent.getSliceTextField().setText(chunk.getSlicePresentation());
          myComponent.getFormatTextField().setText(chunk.getFormat());
          myDialog.setTitle(getTitlePresentation(chunk.getSlicePresentation()));
          myTableCellRenderer = new ArrayTableCellRenderer(Double.MIN_VALUE, Double.MIN_VALUE, chunk.getType());
          fillColorRange(chunk.getMin(), chunk.getMax());
          if (!isNumeric()) {
            disableColor();
          }
          else {
            myComponent.getColoredCheckbox().setEnabled(true);
          }

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

  private static String getTitlePresentation(String slice) {
    return "Array View: " + slice;
  }

  private void fillColorRange(String minValue, String maxValue) {
    double min;
    double max;
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
  }

  public String getSliceText() {
    return myComponent.getSliceTextField().getText();
  }

  public boolean isNumeric() {
    if (myDtypeKind != null) {
      return "biufc".contains(myDtypeKind.substring(0, 1));
    }
    return false;
  }

  private void initTableModel(final boolean inPlace) {
    myPagingModel = new AsyncArrayTableModel(myPagingModel.getRowCount(), myPagingModel.getColumnCount(), this);

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

  private void doReslice() {
    clearErrorMessage();
    init(getSliceText(), false);
  }

  private void clearErrorMessage() {
    showError(null);
  }

  private void doApplyFormat() {
    reset();
  }

  private void reset() {
    clearErrorMessage();
    initTableModel(true);
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

  public String getNodeFullName() {
    return getDebugValue().getEvaluationExpression();
  }

  public String getFormat() {
    return myComponent.getFormatTextField().getText();
  }
}
