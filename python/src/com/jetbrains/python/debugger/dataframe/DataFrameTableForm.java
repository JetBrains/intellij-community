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

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.debugger.array.ArrayTableForm;
import com.jetbrains.python.debugger.array.JBTableWithRowHeaders;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.KeyListener;

/**
 * Created by Yuli Fiterman on 4/26/2016.
 */
public class DataFrameTableForm {
  private final Project myProject;
  private final KeyListener myReformatCallback;
  private final KeyListener myResliceCallback;
  private JBScrollPane myScrollPane;
  private JCheckBox myColoredCheckbox;
  private JPanel myFormatPanel;
  private EditorTextField mySliceTextField;
  private JPanel myMainPanel;
  private JBTable myTable;
  private JLabel myFormatLabel;
  private EditorTextField myFormatTextField;


  public DataFrameTableForm(@NotNull Project project, KeyListener resliceCallback, KeyListener reformatCallback) {
    myProject = project;
    myResliceCallback = resliceCallback;
    myReformatCallback = reformatCallback;

  }
  private void createUIComponents() {
    mySliceTextField = new EditorTextField("", myProject, PythonFileType.INSTANCE) {
      @Override
      protected EditorEx createEditor() {
        EditorEx editor = super.createEditor();
        editor.getContentComponent().addKeyListener(myResliceCallback);
        return editor;
      }
    };

    myTable = new JBTable();
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    myTable.setRowSelectionAllowed(false);
    myTable.setMaxItemsForSizeCalculation(50);

    myTable.getTableHeader().setReorderingAllowed(false);
    myScrollPane = new JBScrollPane();

    myFormatTextField = new EditorTextField("", myProject, PythonFileType.INSTANCE) {
      @Override
      protected EditorEx createEditor() {
        EditorEx editor = super.createEditor();
        editor.getContentComponent().addKeyListener(myReformatCallback);
        return editor;
      }
    };
  }

  public JBTable getTable() {
    return myTable;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public EditorTextField getSliceTextField() {
    return mySliceTextField;
  }

  public JCheckBox getColoredCheckbox() {
    return myColoredCheckbox;
  }

  public JBScrollPane getScrollPane() {
    return myScrollPane;
  }

  public EditorTextField getFormatTextField() {
    return myFormatTextField;
  }
}
