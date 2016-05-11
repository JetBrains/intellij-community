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
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.jetbrains.python.debugger.containerview.NumericContainerRendererForm;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.KeyListener;

/**
 * Created by Yuli Fiterman on 4/26/2016.
 */
public class DataFrameTableForm extends NumericContainerRendererForm {


  public DataFrameTableForm(@NotNull Project project, KeyListener resliceCallback, KeyListener reformatCallback) {
    super(project, resliceCallback, reformatCallback);
  }

  protected void createUIComponents() {

    super.createUIComponents();
    myTable = new JBTable();
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    myTable.setRowSelectionAllowed(false);
    myTable.setMaxItemsForSizeCalculation(50);

    myTable.getTableHeader().setReorderingAllowed(false);
    myScrollPane = new JBScrollPane();
  }
}
