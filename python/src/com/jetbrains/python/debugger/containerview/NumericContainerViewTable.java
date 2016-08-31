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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.array.AsyncArrayTableModel;
import com.jetbrains.python.debugger.array.TableChunkDatasource;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Created by Yuli Fiterman 5/10/2016.
 */
public abstract class NumericContainerViewTable implements TableChunkDatasource {
  protected final static int COLUMNS_IN_DEFAULT_VIEW = 1000;
  protected final static int ROWS_IN_DEFAULT_VIEW = 1000;
  protected static final Logger LOG = Logger.getInstance("#com.jetbrains.python.debugger.containerview.NumericContainerViewTable");
  protected final PyDebugValue myValue;
  protected final ViewNumericContainerDialog myDialog;
  protected final NumericContainerRendererForm myComponent;
  protected final JTable myTable;
  protected final Project myProject;
  protected String myDtypeKind;
  protected ColoredCellRenderer myTableCellRenderer;
  protected AsyncArrayTableModel myPagingModel;

  public NumericContainerViewTable(
    @NotNull Project project, @NotNull ViewNumericContainerDialog dialog, @NotNull PyDebugValue value) {
    myProject = project;
    myDialog = dialog;
    myComponent = createForm(project, new KeyAdapter() {
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
    myValue = value;
    myTable = myComponent.getTable();
  }


  private void initUi(@NotNull final ArrayChunk chunk, final boolean inPlace) {
    myPagingModel = createTableModel(Math.min(chunk.getRows(), ROWS_IN_DEFAULT_VIEW),
                                     Math.min(chunk.getColumns(), COLUMNS_IN_DEFAULT_VIEW));
    myPagingModel.addToCache(chunk);
    myDtypeKind = chunk.getType();

    UIUtil.invokeLaterIfNeeded(() -> {
      myTable.setModel(myPagingModel);
      myComponent.getSliceTextField().setText(chunk.getSlicePresentation());
      myComponent.getFormatTextField().setText(chunk.getFormat());
      myDialog.setTitle(getTitlePresentation(chunk.getSlicePresentation()));
      boolean shouldSetColored = myTableCellRenderer == null || myTableCellRenderer.getColored();
      myTableCellRenderer = createCellRenderer(Double.MIN_VALUE, Double.MIN_VALUE, chunk);
      if (!isNumeric()) {
        disableColor();
      }
      else {
        myComponent.getColoredCheckbox().setEnabled(true);
        myComponent.getColoredCheckbox().setSelected(shouldSetColored);
        myTableCellRenderer.setColored(shouldSetColored);
      }

      if (!inPlace) {
        myComponent.getScrollPane().getViewport().setViewPosition(new Point(0, 0));
      }
      ((AsyncArrayTableModel)myTable.getModel()).fireTableDataChanged();
      ((AsyncArrayTableModel)myTable.getModel()).fireTableCellUpdated(0, 0);
      if (myTable.getColumnCount() > 0) {
        myTable.setDefaultRenderer(myTable.getColumnClass(0), myTableCellRenderer);
      }
    });
  }

  private void disableColor() {
    myTableCellRenderer.setColored(false);
    UIUtil.invokeLaterIfNeeded(() -> {
      myComponent.getColoredCheckbox().setSelected(false);
      myComponent.getColoredCheckbox().setEnabled(false);
      if (myTable.getColumnCount() > 0) {
        myTable.setDefaultRenderer(myTable.getColumnClass(0), myTableCellRenderer);
      }
    });
  }

  protected abstract AsyncArrayTableModel createTableModel(int rowCount, int columnCount);

  protected abstract ColoredCellRenderer createCellRenderer(double minValue, double maxValue, ArrayChunk chunk);

  private void initTableModel(final boolean inPlace) {
    myPagingModel = createTableModel(myPagingModel.getRowCount(), myPagingModel.getColumnCount());

    UIUtil.invokeLaterIfNeeded(() -> {
      myTable.setModel(myPagingModel);
      if (!inPlace) {
        myComponent.getScrollPane().getViewport().setViewPosition(new Point(0, 0));
      }
      ((AsyncArrayTableModel)myTable.getModel()).fireTableDataChanged();
      ((AsyncArrayTableModel)myTable.getModel()).fireTableCellUpdated(0, 0);
      if (myTable.getColumnCount() > 0) {
        myTable.setDefaultRenderer(myTable.getColumnClass(0), myTableCellRenderer);
      }
    });
  }

  @NotNull
  private static NumericContainerRendererForm createForm(@NotNull Project project, KeyListener resliceCallback, KeyAdapter formatCallback) {
    return new NumericContainerRendererForm(project, resliceCallback, formatCallback);
  }

  protected abstract String getTitlePresentation(String slice);


  public final NumericContainerRendererForm getComponent() {
    return myComponent;
  }

  private void initComponent() {
    myComponent.getColoredCheckbox().setEnabled(false);
    myComponent.getColoredCheckbox().addItemListener(e -> {
      if (e.getSource() == myComponent.getColoredCheckbox()) {

        if (myTable.getRowCount() > 0 &&
            myTable.getColumnCount() > 0 &&
            myTable.getCellRenderer(0, 0) instanceof ColoredCellRenderer) {
          ColoredCellRenderer renderer = (ColoredCellRenderer)myTable.getCellRenderer(0, 0);
          if (myComponent.getColoredCheckbox().isSelected()) {
            renderer.setColored(true);
          }
          else {
            renderer.setColored(false);
          }
        }
        if (myTableCellRenderer != null) {
          myTableCellRenderer.setColored(myComponent.getColoredCheckbox().isSelected());
        }
        myComponent.getScrollPane().repaint();
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

  public PyDebugValue getDebugValue() {
    return myValue;
  }

  public void init() {
    init(getDebugValue().getEvaluationExpression(), false);
  }

  public void init(final String slice, final boolean inPlace) {
    initComponent();
    myComponent.getSliceTextField().setText(slice);

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      final PyDebugValue value = getDebugValue();
      PyDebugValue parent = value.getParent();
      final PyDebugValue slicedValue =
        new PyDebugValue(slice, value.getType(), null, value.getValue(), value.isContainer(), value.isReturnedVal(), value.isErrorOnEval(),
                         parent, value.getFrameAccessor());

      final String format = getFormat().isEmpty() ? "%" : getFormat();

      try {
        initUi(value.getFrameAccessor()
                 .getArrayItems(slicedValue, 0, 0, -1, -1, format), inPlace);
      }
      catch (PyDebuggerException e) {
        showError(e.getMessage());
      }
    });
  }


  public String getSliceText() {
    return myComponent.getSliceTextField().getText();
  }

  @Override
  public ArrayChunk getChunk(int rowOffset, int colOffset, int rows, int cols) throws PyDebuggerException {
    final PyDebugValue slicedValue =
      new PyDebugValue(getSliceText(), myValue.getType(), myValue.getTypeQualifier(), myValue.getValue(), myValue.isContainer(),
                       myValue.isErrorOnEval(), myValue.isReturnedVal(),
                       myValue.getParent(), myValue.getFrameAccessor());

    final String format = getFormat().isEmpty() ? "%" : getFormat();
    return myValue.getFrameAccessor().getArrayItems(slicedValue, rowOffset, colOffset, rows, cols, format);
  }

  public abstract boolean isNumeric();


  @Override
  public final String correctStringValue(@NotNull Object value) {
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

  @Override
  public final void showError(String message) {
    myDialog.setError(message);
  }

  protected final void doReslice() {
    clearErrorMessage();
    init(getSliceText(), false);
  }

  private void clearErrorMessage() {
    showError(null);
  }

  protected final void doApplyFormat() {
    reset();
  }

  private void reset() {
    clearErrorMessage();
    initTableModel(true);
  }

  public final String getNodeFullName() {
    return getDebugValue().getEvaluationExpression();
  }

  public final String getFormat() {
    return myComponent.getFormatTextField().getText();
  }
}
