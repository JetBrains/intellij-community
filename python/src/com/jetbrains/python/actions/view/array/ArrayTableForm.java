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

import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author amarch
 */
public class ArrayTableForm {
  private EditorTextField mySliceTextField;
  private JCheckBox myColoredCheckbox;
  private EditorTextField myFormatTextField;
  private JBScrollPane myScrollPane;
  private JLabel myFormatLabel;
  private JPanel myFormatPanel;
  private JPanel myMainPanel;
  public JBTable myTable;
  private Project myProject;


  private static final String DATA_LOADING_IN_PROCESS = "Please wait, load array data.";
  private static final String NOT_APPLICABLE = "View not applicable for ";

  public ArrayTableForm(Project project) {
    myProject = project;
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

    mySliceTextField = new EditorTextField("", myProject, PythonFileType.INSTANCE);

    ((JBTableWithRows)myTable).setRowNumberTable((RowNumberTable)rowTable);
    ((JBTableWithRows)myTable).setSliceField(mySliceTextField);

    myFormatTextField = new EditorTextField("", myProject, PythonFileType.INSTANCE);
  }

  public class JBTableWithRows extends JBTable {
    private RowNumberTable myRowNumberTable;
    private EditorTextField mySliceField;

    public boolean getScrollableTracksViewportWidth() {
      return getPreferredSize().width < getParent().getWidth();
    }

    public RowNumberTable getRowNumberTable() {
      return myRowNumberTable;
    }

    public void setRowNumberTable(RowNumberTable rowNumberTable) {
      myRowNumberTable = rowNumberTable;
    }

    public EditorTextField getSliceField() {
      return mySliceField;
    }

    public void setSliceField(EditorTextField sliceField) {
      mySliceField = sliceField;
    }
  }

  public EditorTextField getSliceTextField() {
    return mySliceTextField;
  }

  public EditorTextField getFormatTextField() {
    return myFormatTextField;
  }

  public JBTable getTable() {
    return myTable;
  }

  public JCheckBox getColoredCheckbox() {
    return myColoredCheckbox;
  }

  public void setDefaultStatus() {
    if (myTable != null) {
      myTable.getEmptyText().setText(DATA_LOADING_IN_PROCESS);
      myTable.setPaintBusy(true);
    }
  }

  public void setNotApplicableStatus(XValueNodeImpl node) {
    myTable.getEmptyText().setText(NOT_APPLICABLE + node.getName());
  }

  public JComponent getMainPanel() {
    return myMainPanel;
  }

  public JBScrollPane getScrollPane() {
    return myScrollPane;
  }
}
