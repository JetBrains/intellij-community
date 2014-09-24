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

import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author amarch
 */
public class ArrayTableForm {
  private JTextField mySliceTextField;
  private JCheckBox myColoredCheckbox;
  private JTextField myFormatTextField;
  private JBScrollPane myScrollPane;
  private JLabel myFormatLabel;
  private JPanel myFormatPanel;
  private JPanel myMainPanel;
  public JBTable myTable;
  private PyViewArrayAction.MyDialog myParentDialog;


  private static final String DATA_LOADING_IN_PROCESS = "Please wait, load array data.";

  private static final String NOT_APPLICABLE = "View not applicable for ";

  public ArrayTableForm(PyViewArrayAction.MyDialog dialog) {
    myParentDialog = dialog;
  }


  public class JBTableWithRows extends JBTable {
    private RowNumberTable myRowNumberTable;

    public boolean getScrollableTracksViewportWidth() {
      return getPreferredSize().width < getParent().getWidth();
    }

    public RowNumberTable getRowNumberTable() {
      return myRowNumberTable;
    }

    public void setRowNumberTable(RowNumberTable rowNumberTable) {
      myRowNumberTable = rowNumberTable;
    }
  }

  private void createUIComponents() {
    myTable = new JBTableWithRows();

    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    myTable.setRowSelectionAllowed(false);
    myTable.getTableHeader().setReorderingAllowed(false);

    myScrollPane = new JBScrollPane();
    JTable rowTable = new RowNumberTable(myTable) {
      @Override
      protected void paintComponent(@NotNull Graphics g) {
        getEmptyText().setText("");
        super.paintComponent(g);
      }
    };
    myScrollPane.setRowHeaderView(rowTable);
    myScrollPane.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER,
                           rowTable.getTableHeader());

    ((JBTableWithRows)myTable).setRowNumberTable((RowNumberTable)rowTable);

    myScrollPane.getHorizontalScrollBar()
      .addAdjustmentListener(new TableAdjustmentListener(myTable, 100, 100, TableAdjustmentListener.HORIZONTAL_MODE, 50));

    myScrollPane.getVerticalScrollBar()
      .addAdjustmentListener(new TableAdjustmentListener(myTable, 100, 100, TableAdjustmentListener.VERTICAL_MODE, 50));
  }

  public JTextField getSliceTextField() {
    return mySliceTextField;
  }

  public JTextField getFormatTextField() {
    return myFormatTextField;
  }

  public JBTable getTable() {
    return myTable;
  }

  public JCheckBox getColored() {
    return myColoredCheckbox;
  }

  public void setDefaultStatus() {
    if (myTable != null) {
      myTable.getEmptyText().setText(DATA_LOADING_IN_PROCESS);
      myTable.setPaintBusy(true);
    }
  }

  public void setErrorText(Exception e) {
    setErrorText(e.getMessage());
  }

  public void setErrorText(String message) {
    myParentDialog.setError(message);
  }

  public void setNotApplicableStatus(XValueNodeImpl node) {
    myTable.getEmptyText().setText(NOT_APPLICABLE + node.getName());
  }

  public JComponent getMainPanel() {
    return myMainPanel;
  }
}
