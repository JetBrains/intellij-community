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
import com.jetbrains.python.debugger.containerview.NumericContainerRendererForm;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.KeyListener;

/**
 * @author amarch
 */
public class ArrayTableForm extends NumericContainerRendererForm {


  public ArrayTableForm(@NotNull Project project, KeyListener resliceCallback, KeyListener reformatCallback) {
    super(project, resliceCallback, reformatCallback);
  }

  protected void createUIComponents() {

    super.createUIComponents();
    myTable = new JBTableWithRowHeaders();

    myScrollPane = ((JBTableWithRowHeaders)myTable).getScrollPane();
  }


  public static class ColumnHeaderRenderer extends DefaultTableHeaderCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
      super.getTableCellRendererComponent(table, value, selected, focused, row, column);
      int selectedColumn = table.getSelectedColumn();
      if (selectedColumn == column) {
        setFont(getFont().deriveFont(Font.BOLD));
      }
      return this;
    }
  }

  public static class DefaultTableHeaderCellRenderer extends DefaultTableCellRenderer {

    public DefaultTableHeaderCellRenderer() {
      setHorizontalAlignment(CENTER);
      setHorizontalTextPosition(LEFT);
      setVerticalAlignment(BOTTOM);
      setOpaque(false);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, value,
                                          isSelected, hasFocus, row, column);
      JTableHeader tableHeader = table.getTableHeader();
      if (tableHeader != null) {
        setForeground(tableHeader.getForeground());
      }
      setBorder(UIManager.getBorder("TableHeader.cellBorder"));
      return this;
    }
  }
}
