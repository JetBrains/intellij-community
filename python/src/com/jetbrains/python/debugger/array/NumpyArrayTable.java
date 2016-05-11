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
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.containerview.NumericContainerViewTable;
import com.jetbrains.python.debugger.containerview.ViewNumericContainerDialog;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyListener;


/**
 * @author amarch
 */
public final class NumpyArrayTable extends NumericContainerViewTable {


  private ArrayTableCellRenderer myArrayTableCellRenderer;
  public NumpyArrayTable(@NotNull Project project,
                         @NotNull ViewNumericContainerDialog dialog, @NotNull PyDebugValue value) {
    super(project, dialog, value);
  }

  @Override
  protected final String getTitlePresentation(String slice) {
    return "Array View: " + slice;
  }

  public void disableColor() {
    myArrayTableCellRenderer.setMin(Double.MAX_VALUE);
    myArrayTableCellRenderer.setMax(Double.MIN_VALUE);
    myTableCellRenderer.setColored(false);
    UIUtil.invokeLaterIfNeeded(() -> {
      myComponent.getColoredCheckbox().setSelected(false);
      myComponent.getColoredCheckbox().setEnabled(false);
      if (myTable.getColumnCount() > 0) {
        myTable.setDefaultRenderer(myTable.getColumnClass(0), myArrayTableCellRenderer);
      }
    });
  }


  @Override
  protected final void initUi(@NotNull final ArrayChunk chunk, final boolean inPlace) {
    myPagingModel = new AsyncArrayTableModel(Math.min(chunk.getRows(), ROWS_IN_DEFAULT_VIEW),
                                             Math.min(chunk.getColumns(), COLUMNS_IN_DEFAULT_VIEW), this);
    myPagingModel.addToCache(chunk);
    myDtypeKind = chunk.getType();

    UIUtil.invokeLaterIfNeeded(() -> {
      myTable.setModel(myPagingModel);
      myComponent.getSliceTextField().setText(chunk.getSlicePresentation());
      myComponent.getFormatTextField().setText(chunk.getFormat());
      myDialog.setTitle(getTitlePresentation(chunk.getSlicePresentation()));
      myArrayTableCellRenderer = new ArrayTableCellRenderer(Double.MIN_VALUE, Double.MIN_VALUE, chunk.getType());
      myTableCellRenderer = myArrayTableCellRenderer;
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
        myTable.setDefaultRenderer(myTable.getColumnClass(0), myArrayTableCellRenderer);
      }
    });
  }

  private void fillColorRange(String minValue, String maxValue) {
    double min;
    double max;
    if ("c".equals(myDtypeKind)) {
      min = 0;
      max = 1;
      myArrayTableCellRenderer.setComplexMin(minValue);
      myArrayTableCellRenderer.setComplexMax(maxValue);
    }
    else if ("b".equals(myDtypeKind)) {
      min = minValue.equals("True") ? 1 : 0;
      max = maxValue.equals("True") ? 1 : 0;
    }
    else {
      min = Double.parseDouble(minValue);
      max = Double.parseDouble(maxValue);
    }

    myArrayTableCellRenderer.setMin(min);
    myArrayTableCellRenderer.setMax(max);
  }

  @Override
  public boolean isNumeric() {
    if (myDtypeKind != null) {
      return "biufc".contains(myDtypeKind.substring(0, 1));
    }
    return false;
  }

  @Override
  protected final void initTableModel(final boolean inPlace) {
    myPagingModel = new AsyncArrayTableModel(myPagingModel.getRowCount(), myPagingModel.getColumnCount(), this);

    UIUtil.invokeLaterIfNeeded(() -> {
      myTable.setModel(myPagingModel);
      if (!inPlace) {
        myComponent.getScrollPane().getViewport().setViewPosition(new Point(0, 0));
        JBTableWithRowHeaders.RowHeaderTable rowTable = ((JBTableWithRowHeaders)myTable).getRowHeaderTable();
        rowTable.setRowShift(0);
      }
      ((AsyncArrayTableModel)myTable.getModel()).fireTableDataChanged();
      ((AsyncArrayTableModel)myTable.getModel()).fireTableCellUpdated(0, 0);
      if (myTable.getColumnCount() > 0) {
        myTable.setDefaultRenderer(myTable.getColumnClass(0), myArrayTableCellRenderer);
      }
    });
  }

  @Override
  @NotNull
  protected ArrayTableForm createForm(@NotNull Project project, KeyListener resliceCallback, KeyAdapter formatCallback) {
    return new ArrayTableForm(project,resliceCallback, formatCallback);
  }
}
