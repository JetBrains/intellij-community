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

  private void createUIComponents() {
    myTable = new JBTable() {
      public boolean getScrollableTracksViewportWidth() {
        return getPreferredSize().width < getParent().getWidth();
      }
    };
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    myTable.setRowSelectionAllowed(false);

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
  }

  public JTextField getSliceTextField() {
    return mySliceTextField;
  }

  public JTextField getFormatTextField() {
    return myFormatTextField;
  }

  public JTable getTable() {
    return myTable;
  }

  public JCheckBox getColored() {
    return myColoredCheckbox;
  }

  public void setDefaultStatus() {
    myTable.getEmptyText().setText(DATA_LOADING_IN_PROCESS);
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
