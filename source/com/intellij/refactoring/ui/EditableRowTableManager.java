package com.intellij.refactoring.ui;

import com.intellij.ui.TableUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author dsl
 */
public class EditableRowTableManager  {
  private JButton myAddButton;
  private JButton myRemoveButton;
  private JButton myUpButton;
  private JButton myDownButton;
  private final JTable myTable;
  private final RowEditableTableModel myTableModel;
  private final JPanel myButtonsPanel;

  public EditableRowTableManager(final JTable table, final RowEditableTableModel tableModel, boolean addMnemonics) {
    myTable = table;
    myTableModel = tableModel;
    myButtonsPanel = createButtonsPanel(addMnemonics);
  }

  public JPanel getButtonsPanel() {
    return myButtonsPanel;
  }

  private JPanel createButtonsPanel(boolean addMnemonics) {
    JPanel buttonsPanel = new JPanel();
    buttonsPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

    buttonsPanel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.insets = new Insets(2, 4, 2, 4);

    myAddButton = new JButton("Add");
    if (addMnemonics) myAddButton.setMnemonic('A');
    myAddButton.setDefaultCapable(false);
    buttonsPanel.add(myAddButton, gbConstraints);

    myRemoveButton = new JButton("Remove");
    if (addMnemonics) myRemoveButton.setMnemonic('E');
    myRemoveButton.setDefaultCapable(false);
    buttonsPanel.add(myRemoveButton, gbConstraints);

    myUpButton = new JButton("Move Up");
    if (addMnemonics) myUpButton.setMnemonic('U');
    myUpButton.setDefaultCapable(false);
    buttonsPanel.add(myUpButton, gbConstraints);

    myDownButton = new JButton("Move Down");
    if (addMnemonics) myDownButton.setMnemonic('D');
    myDownButton.setDefaultCapable(false);
    buttonsPanel.add(myDownButton, gbConstraints);

    gbConstraints.weighty = 1;
    buttonsPanel.add(new JPanel(), gbConstraints);

    myAddButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          TableUtil.stopEditing(myTable);
          myTableModel.addRow();
          final int index = myTableModel.getRowCount() - 1;
          myTable.editCellAt(index, 0);
          myTable.setRowSelectionInterval(index, index);
          myTable.setColumnSelectionInterval(0, 0);
          final Component editorComponent = myTable.getEditorComponent();
          if (editorComponent != null) {
            final Rectangle bounds = editorComponent.getBounds();
            myTable.scrollRectToVisible(bounds);
            editorComponent.requestFocus();
          }
        }
      }
    );

    myRemoveButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          TableUtil.stopEditing(myTable);
          int index = myTable.getSelectedRow();
          if (0 <= index && index < myTableModel.getRowCount()) {
            myTableModel.removeRow(index);
            if (index < myTableModel.getRowCount()) {
              myTable.setRowSelectionInterval(index, index);
            }
            else {
              if (index > 0) {
                myTable.setRowSelectionInterval(index - 1, index - 1);
              }
            }
            updateButtons();
          }

          myTable.requestFocus();
        }
      }
    );

    myUpButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          TableUtil.stopEditing(myTable);
          int index = myTable.getSelectedRow();
          if (0 < index && index < myTableModel.getRowCount()) {
            myTableModel.exchangeRows(index, index - 1);
            myTable.setRowSelectionInterval(index - 1, index - 1);
          }
          myTable.requestFocus();
        }
      }
    );

    myDownButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          TableUtil.stopEditing(myTable);
          int index = myTable.getSelectedRow();
          if (0 <= index && index < myTableModel.getRowCount() - 1) {
            myTableModel.exchangeRows(index, index + 1);
            myTable.setRowSelectionInterval(index + 1, index + 1);
          }
          myTable.requestFocus();
        }
      }
    );

    myTable.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          updateButtons();
        }
      }
    );
    updateButtons();

    return buttonsPanel;
  }

  public void updateButtons() {
    int index = myTable.getSelectedRow();
    if (0 <= index && index < myTableModel.getRowCount()) {
      myRemoveButton.setEnabled(true);
      myUpButton.setEnabled(index > 0);
      myDownButton.setEnabled(index < myTableModel.getRowCount() - 1);
    }
    else {
      myRemoveButton.setEnabled(false);
      myUpButton.setEnabled(false);
      myDownButton.setEnabled(false);
    }
    myAddButton.setEnabled(true);
  }
}
