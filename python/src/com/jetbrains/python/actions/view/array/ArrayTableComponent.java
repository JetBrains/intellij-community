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
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author amarch
 */
class ArrayTableComponent extends JPanel {
  private JScrollPane myScrollPane;
  private JTextField myTextField;
  private JBTable myTable;
  private JCheckBox myCheckBox;

  private static final String DATA_LOADING_IN_PROCESS = "Please wait, load array data.";

  private static final String NOT_APPLICABLE = "View not applicable for ";

  public ArrayTableComponent() {
    super(new GridBagLayout());

    myTextField = new JTextField();
    myTextField.setToolTipText("Current slice");
    myTextField.setEditable(false);

    myTable = new JBTable() {
      public boolean getScrollableTracksViewportWidth() {
        return getPreferredSize().width < getParent().getWidth();
      }
    };
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    myTable.setRowSelectionAllowed(false);

    myCheckBox = new JCheckBox();
    myCheckBox.setText("Colored");
    myCheckBox.setSelected(true);
    myCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getSource() == myCheckBox) {
          if (myTable.getColumnCount() > 0 && myTable.getCellRenderer(0, 0) instanceof ArrayTableCellRenderer) {
            ArrayTableCellRenderer renderer = (ArrayTableCellRenderer)myTable.getCellRenderer(0, 0);
            if (myCheckBox.isSelected()) {
              renderer.setColored(true);
            }
            else {
              renderer.setColored(false);
            }
          }
          myScrollPane.repaint();
        }
      }
    });

    myScrollPane = new JBScrollPane(myTable);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

    JTable rowTable = new RowNumberTable(myTable);
    myScrollPane.setRowHeaderView(rowTable);
    myScrollPane.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER,
                           rowTable.getTableHeader());

    add(myScrollPane,
        new GridBagConstraints(0, 0, 2, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    add(myTextField,
        new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.SOUTH, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    add(myCheckBox,
        new GridBagConstraints(1, 1, 1, 1, 1, 0, GridBagConstraints.SOUTH, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
  }

  public JTextField getTextField() {
    return myTextField;
  }

  public JBTable getTable() {
    return myTable;
  }

  public JCheckBox getColored() {
    return myCheckBox;
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
    //todo: Access to realized (ever shown) UI components
    // should be done only from the AWT event dispatch thread,
    // revalidate(), invalidate() & repaint() is ok from any thread
    setSpinnerText(message);
  }

  public void setNotApplicableSpinner(XValueNodeImpl node) {
    setSpinnerText(NOT_APPLICABLE + node.getName());
  }
}
