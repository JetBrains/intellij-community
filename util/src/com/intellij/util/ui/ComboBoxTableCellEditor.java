package com.intellij.util.ui;

import com.intellij.util.ListWithSelection;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
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
    myComboBox.setRenderer(new BasicComboBoxRenderer());
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

    return myPanel;
  }

  public Object getCellEditorValue() {
    return myComboBox.getSelectedItem();
  }

  public Dimension getPreferedSize() {
    return myComboBox.getPreferredSize();
  }

}
