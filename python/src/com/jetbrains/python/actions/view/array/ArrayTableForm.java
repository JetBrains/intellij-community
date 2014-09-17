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

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

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


  private static final String DATA_LOADING_IN_PROCESS = "Please wait, load array data.";

  private static final String NOT_APPLICABLE = "View not applicable for ";

  public ArrayTableForm(){
  }

  private void createUIComponents() {
    myTable = new JBTable() {
      public boolean getScrollableTracksViewportWidth() {
        return getPreferredSize().width < getParent().getWidth();
      }
    };
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    myTable.setRowSelectionAllowed(false);
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

  private void setSpinnerText(String text) {
    DefaultTableModel model = new DefaultTableModel(1, 1) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }
    };
    myTable.setModel(model);
    myTable.setValueAt(text, 0, 0);
  }

  public void setDefaultSpinnerText() {
    setSpinnerText(DATA_LOADING_IN_PROCESS);
  }

  public void setErrorSpinnerText(Exception e) {
    setSpinnerText(e.getMessage());
  }

  public void setErrorSpinnerText(String message) {
    setSpinnerText(message);
  }


  public void setNotApplicableSpinner(XValueNodeImpl node) {
    setSpinnerText(NOT_APPLICABLE + node.getName());
  }

  public JComponent getMainPanel() {
    return myMainPanel;
  }
}
