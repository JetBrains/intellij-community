package com.intellij.util.ui;

import com.intellij.util.ListWithSelection;

import javax.swing.*;
import java.awt.*;
import java.util.Iterator;

/**
 * author: lesya
 */
public class ComboBoxTableCellEditor extends AbstractTableCellEditor {
  public final static ComboBoxTableCellEditor INSTANCE = new ComboBoxTableCellEditor();

  private JPanel myPanel = new JPanel(new GridBagLayout());
  private JComboBox myComboBox = new JComboBox();

  private ComboBoxTableCellEditor() {
    myComboBox.setRenderer(new MyComboboxRenderer());
    myPanel.add(
      myComboBox,
        new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0)
    );
  }

  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    final ListWithSelection options = (ListWithSelection)value;
    if (options.getSelection() == null) {
      options.selectFirst();
    }
    myComboBox.removeAllItems();
    for (Iterator each = options.iterator(); each.hasNext();) {
      myComboBox.addItem(each.next());
    }

    myComboBox.setSelectedItem(options.getSelection());

    if (isSelected) {
      myComboBox.setBackground(table.getSelectionBackground());
      myPanel.setBackground(table.getSelectionBackground());
      myComboBox.setForeground(table.getSelectionForeground());
      myPanel.setForeground(table.getSelectionForeground());
    }
    else {
      myComboBox.setBackground(table.getBackground());
      myPanel.setBackground(table.getBackground());
      myComboBox.setForeground(table.getForeground());
      myPanel.setForeground(table.getForeground());
    }

    return myPanel;

  }

  public Object getCellEditorValue() {
    return myComboBox.getSelectedItem();
  }

  private static class MyComboboxRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (component instanceof JLabel) {
          ( (JLabel)component).setHorizontalAlignment(SwingConstants.CENTER);
      }
      return component;
    }
  }

  public Dimension getPreferedSize() {
    return myComboBox.getPreferredSize();
  }

}
