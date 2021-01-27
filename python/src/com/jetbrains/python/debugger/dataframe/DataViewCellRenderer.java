// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.dataframe;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class DataViewCellRenderer extends DefaultTableCellRenderer {
  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (!isSelected)
      colorize(table, value, isSelected, hasFocus, row, column);

    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    return this;
  }

  protected void colorize(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) { }
}
