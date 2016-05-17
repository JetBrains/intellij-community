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
package com.jetbrains.python.debugger.dataframe;

import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.debugger.*;
import com.jetbrains.python.debugger.array.AsyncArrayTableModel;
import com.jetbrains.python.debugger.containerview.NumericContainerViewTable;
import com.jetbrains.python.debugger.array.TableChunkDatasource;
import com.jetbrains.python.debugger.containerview.NumericContainerRendererForm;
import com.jetbrains.python.debugger.containerview.ViewNumericContainerDialog;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.*;

/**
 * @author amarch
 */
  /* A bunch of this is copied from NumpyArrayTable*/

public class DataFrameTable extends NumericContainerViewTable implements TableChunkDatasource {

  private Project myProject;
  private DataFrameTableCellRenderer myDataFrameCellRenderer;

  public DataFrameTable(@NotNull Project project,
                        @NotNull ViewNumericContainerDialog dialog, @NotNull PyDebugValue value) {
    super(project, dialog, value);
  }

  @NotNull
  @Override
  protected NumericContainerRendererForm createForm(@NotNull Project project, KeyListener resliceCallback, KeyAdapter formatCallback) {
    return new DataFrameTableForm(project,resliceCallback,formatCallback);

  }


  protected final void initUi(@NotNull final ArrayChunk chunk, final boolean inPlace) {
    myPagingModel = new DataFrameTableModel(Math.min(chunk.getRows(), ROWS_IN_DEFAULT_VIEW),
                                            Math.min(chunk.getColumns(), COLUMNS_IN_DEFAULT_VIEW), this);
    myPagingModel.addToCache(chunk);

    UIUtil.invokeLaterIfNeeded(() -> {
      myTable.setModel(myPagingModel);
      myComponent.getSliceTextField().setText(chunk.getSlicePresentation());
      myComponent.getFormatTextField().setText(chunk.getFormat());
      myDialog.setTitle(getTitlePresentation(chunk.getSlicePresentation()));
      myDataFrameCellRenderer = new DataFrameTableCellRenderer();
      myTableCellRenderer = myDataFrameCellRenderer;
      myComponent.getColoredCheckbox().setEnabled(true);


      if (!inPlace) {
        myComponent.getScrollPane().getViewport().setViewPosition(new Point(0, 0));
      }
      ((AsyncArrayTableModel)myTable.getModel()).fireTableDataChanged();
      ((AsyncArrayTableModel)myTable.getModel()).fireTableCellUpdated(0, 0);
      myTable.setDefaultRenderer(TableValueDescriptor.class, myDataFrameCellRenderer);
    });
  }

  @Override
  public boolean isNumeric() {
    return false;
  }

  protected final String getTitlePresentation(String slice) {
    return "DataFrame View: " + slice;
  }


  protected final void initTableModel(final boolean inPlace) {
    myPagingModel = new DataFrameTableModel(myPagingModel.getRowCount(), myPagingModel.getColumnCount() - 1, this);

    UIUtil.invokeLaterIfNeeded(() -> {
      myTable.setModel(myPagingModel);
      if (!inPlace) {
        myComponent.getScrollPane().getViewport().setViewPosition(new Point(0, 0));
      }
      ((AsyncArrayTableModel)myTable.getModel()).fireTableDataChanged();
      ((AsyncArrayTableModel)myTable.getModel()).fireTableCellUpdated(0, 0);
      myTable.setDefaultRenderer(TableValueDescriptor.class, myDataFrameCellRenderer);
    });
  }
}
