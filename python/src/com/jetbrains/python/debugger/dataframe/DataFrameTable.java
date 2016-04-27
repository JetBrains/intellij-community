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

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.debugger.*;
import com.jetbrains.python.debugger.array.AsyncArrayTableModel;
import com.jetbrains.python.debugger.array.TableChunkDatasource;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author amarch
 */
  /* A bunch of this is copied from NumpyArrayTable*/

public class DataFrameTable implements TableChunkDatasource {
  private final PyDebugValue myValue;
  private final PyViewDataFrameAction.ViewDataFrameDialog myDialog;
  private final DataFrameTableForm myComponent;
  private final JTable myTable;
  private Project myProject;

  private DataFrameTableCellRenderer myTableCellRenderer;
  private AsyncArrayTableModel myPagingModel;



  private final static int COLUMNS_IN_DEFAULT_VIEW = 1000;
  private final static int ROWS_IN_DEFAULT_VIEW = 1000;



  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.debugger.array.NumpyArrayValueProvider");

  public DataFrameTable(@NotNull Project project,
                        @NotNull PyViewDataFrameAction.ViewDataFrameDialog dialog, @NotNull PyDebugValue value) {
    myValue = value;
    myDialog = dialog;
    myComponent = new DataFrameTableForm(project, new KeyAdapter() {
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
  }


  private void initComponent() {
    myComponent.getColoredCheckbox().setEnabled(false);
    myComponent.getColoredCheckbox().addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getSource() == myComponent.getColoredCheckbox()) {

          if (myTable.getRowCount() > 0 &&
              myTable.getColumnCount() > 0 &&
              myTable.getCellRenderer(0, 0) instanceof DataFrameTableCellRenderer) {
            DataFrameTableCellRenderer renderer = (DataFrameTableCellRenderer)myTable.getCellRenderer(0, 0);
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
          new PyDebugValue(slice, value.getType(), null, value.getValue(), value.isContainer(), value.isErrorOnEval(),
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
      myPagingModel = new DataFrameTableModel(Math.min(chunk.getRows(), ROWS_IN_DEFAULT_VIEW),
                                               Math.min(chunk.getColumns(), COLUMNS_IN_DEFAULT_VIEW), this);
      myPagingModel.addToCache(chunk);

      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          myTable.setModel(myPagingModel);
          myComponent.getSliceTextField().setText(chunk.getSlicePresentation());
          myComponent.getFormatTextField().setText(chunk.getFormat());
          myDialog.setTitle(getTitlePresentation(chunk.getSlicePresentation()));
          myTableCellRenderer = new DataFrameTableCellRenderer();

          myComponent.getColoredCheckbox().setEnabled(true);


          if (!inPlace) {
            myComponent.getScrollPane().getViewport().setViewPosition(new Point(0, 0));

          }
          ((AsyncArrayTableModel)myTable.getModel()).fireTableDataChanged();
          ((AsyncArrayTableModel)myTable.getModel()).fireTableCellUpdated(0, 0);
           myTable.setDefaultRenderer(TableValueDescriptor.class, myTableCellRenderer);

        }
      });
  }

  private static String getTitlePresentation(String slice) {
    return "DataFrame View: " + slice;
  }



  public String getSliceText() {
    return myComponent.getSliceTextField().getText();
  }

  @Override
  public ArrayChunk getChunk(int rowOffset, int colOffset, int rows, int cols) throws PyDebuggerException {
    final PyDebugValue slicedValue =
      new PyDebugValue(getSliceText(), myValue.getType(), myValue.getTypeQualifier(), myValue.getValue(), myValue.isContainer(), myValue.isErrorOnEval(),
                       myValue.getParent(), myValue.getFrameAccessor());

    return myValue.getFrameAccessor().getArrayItems(slicedValue, rowOffset, colOffset, rows, cols, getFormat());
  }



  private void initTableModel(final boolean inPlace) {
    myPagingModel = new DataFrameTableModel(myPagingModel.getRowCount(), myPagingModel.getColumnCount() - 1, this);

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myTable.setModel(myPagingModel);
        if (!inPlace) {
          myComponent.getScrollPane().getViewport().setViewPosition(new Point(0, 0));

        }
        ((AsyncArrayTableModel)myTable.getModel()).fireTableDataChanged();
        ((AsyncArrayTableModel)myTable.getModel()).fireTableCellUpdated(0, 0);
         myTable.setDefaultRenderer(TableValueDescriptor.class, myTableCellRenderer);

      }
    });
  }

  @Override
  public String correctStringValue(@NotNull Object value) {
    if (value instanceof String) {
      String corrected = (String)value;
        if (corrected.startsWith("\'") || corrected.startsWith("\"")) {
          corrected = corrected.substring(1, corrected.length() - 1);
        }

      return corrected;
    }
    else if (value instanceof Integer) {
      return Integer.toString((Integer)value);
    }
    return value.toString();
  }


  @Override
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


  public String getNodeFullName() {
    return getDebugValue().getEvaluationExpression();
  }

  public String getFormat() {
    return myComponent.getFormatTextField().getText();
  }

  public DataFrameTableForm getComponent() {
    return myComponent;
  }
}
