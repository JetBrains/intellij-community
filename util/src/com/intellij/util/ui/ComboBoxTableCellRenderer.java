/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ListWithSelection;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.Iterator;
import java.util.Arrays;

public class ComboBoxTableCellRenderer extends JPanel implements TableCellRenderer {

  public final static ComboBoxTableCellRenderer INSTANCE = new ComboBoxTableCellRenderer();

  /**
   * DefaultTableCellRenderer, that displays JComboBox on selected value
   */
  public final static TableCellRenderer COMBO_WHEN_SELECTED_RENDERER = new DefaultTableCellRenderer() {
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (isSelected) {
        return INSTANCE.getTableCellRendererComponent(table, new ListWithSelection(Arrays.asList(value)), isSelected, hasFocus, row, column);
      }
      return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }
  };

  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.ComboBoxTableCellRenderer");
  private final JComboBox myCombo = new JComboBox();

  private ComboBoxTableCellRenderer() {
    super(new GridBagLayout());
    add(myCombo,
        new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                               new Insets(0, 0, 0, 0), 0, 0));
  }

  public JComponent getTableCellRendererComponent(JTable table,
                                                 Object value,
                                                 boolean isSelected,
                                                 boolean hasFocus,
                                                 int row,
                                                 int column) {
    LOG.assertTrue(value instanceof ListWithSelection, value.getClass().getName());
    final ListWithSelection tags = (ListWithSelection)value;
    if (tags.getSelection() == null) {
      tags.selectFirst();
    }
    myCombo.removeAllItems();
    for (Iterator each = tags.iterator(); each.hasNext();) {
      myCombo.addItem(each.next());
    }

    myCombo.setSelectedItem(tags.getSelection());

    return this;
  }

}
