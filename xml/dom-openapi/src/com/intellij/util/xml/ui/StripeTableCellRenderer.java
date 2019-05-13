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
package com.intellij.util.xml.ui;

import javax.swing.table.TableCellRenderer;
import javax.swing.*;
import java.awt.*;

/**
 * @author peter
 */
public class StripeTableCellRenderer implements TableCellRenderer {
  private final TableCellRenderer myRenderer;
  private static final double FACTOR = 0.92;

  public StripeTableCellRenderer(final TableCellRenderer renderer) {
    myRenderer = renderer;
  }


  public StripeTableCellRenderer() {
    this(null);
  }

  public static Color darken(Color color) {
    return new Color(Math.max((int)(color.getRed()  *FACTOR), 0),
                     Math.max((int)(color.getGreen()*FACTOR), 0),
                     Math.max((int)(color.getBlue() *FACTOR), 0));
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    final JComponent component = (JComponent)getRenderer(row, column).getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    if (row % 2 != 0 && !isSelected) {
      component.setBackground(darken(table.getBackground()));
    } else {
      component.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
    }
    component.setOpaque(true);
    return component;
  }

  protected TableCellRenderer getRenderer(int row, int column) {
    return myRenderer;
  }
}
