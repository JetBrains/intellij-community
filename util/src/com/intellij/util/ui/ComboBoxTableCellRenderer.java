/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ListWithSelection;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Iterator;

public class ComboBoxTableCellRenderer extends JPanel implements TableCellRenderer {

  public final static ComboBoxTableCellRenderer INSTANCE = new ComboBoxTableCellRenderer();

  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.ComboBoxTableCellRenderer");
  private final JComboBox myCombo = new JComboBox();

  private ComboBoxTableCellRenderer() {
    super(new GridBagLayout());
    add(myCombo,
        new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                               new Insets(0, 0, 0, 0), 0, 0));
  }

  public Component getTableCellRendererComponent(JTable table,
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

    if (isSelected) {
      myCombo.setBackground(table.getSelectionBackground());
      setBackground(table.getSelectionBackground());
      myCombo.setForeground(table.getSelectionForeground());
      setForeground(table.getSelectionForeground());
    }
    else {
      myCombo.setBackground(table.getBackground());
      setBackground(table.getBackground());
      myCombo.setForeground(table.getForeground());
      setForeground(table.getForeground());
    }
    return this;
  }

}
