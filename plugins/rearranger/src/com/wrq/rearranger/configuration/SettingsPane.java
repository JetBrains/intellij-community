/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.configuration;

import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.settings.attributeGroups.AttributeGroup;
import com.wrq.rearranger.settings.attributeGroups.PrioritizedRule;
import com.wrq.rearranger.settings.attributeGroups.Rule;
import com.wrq.rearranger.util.Constraints;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/** Code common to dialog managing creation and maintenance of a list of rules. */
public abstract class SettingsPane {
// ------------------------------ FIELDS ------------------------------

  public static final int SEQUENCE_COLUMN    = 0;
  public static final int PRIORITY_COLUMN    = 1;
  public static final int DESCRIPTION_COLUMN = 2;
  public static final int NUM_COLUMNS        = 3;
  protected final RearrangerSettings settings;

  private final List<AttributeGroup> list;
  private       List<ChoicePanel>    modelData;
  private       JTable               jTable;
  private       AbstractTableModel   model;
  private       JPanel               pane;

// --------------------------- CONSTRUCTORS ---------------------------

  SettingsPane(final RearrangerSettings settings, List<AttributeGroup> list) {
    this.settings = settings;
    this.list = list;
    modelData = new ArrayList<ChoicePanel>(list.size());
    createConfigurationPane();
  }

  private void createConfigurationPane() {
    pane = new JPanel(new GridBagLayout());
    final Border border = BorderFactory.createEmptyBorder();
    pane.setBorder(border);
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.weightedNewRow();
    constraints.fill = GridBagConstraints.BOTH;
    constraints.insets = new Insets(10, 10, 10, 10);
    pane.add(createScrollableTablePanel(), constraints.weightedFirstCol());
    pane.add(createAddRemovePanel(), constraints.nextCol());
  }

  private JPanel createScrollableTablePanel() {
    final JPanel cmlPanel = new JPanel(new GridBagLayout());
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.weightedLastRow();
    constraints.fill = GridBagConstraints.BOTH;
    final JTextField priorityField = new JTextField();
    priorityField.setHorizontalAlignment(JTextField.RIGHT);
    TableCellEditor priorityEditor = new DefaultCellEditor(priorityField);
    model = new AbstractTableModel() {
      public int getColumnCount() {
        return NUM_COLUMNS;  // sequence, priority, rule description
      }

      public int getRowCount() {
        return list.size();
      }

      public Object getValueAt(int rowIndex, int columnIndex) {
        switch (columnIndex) {
          case SEQUENCE_COLUMN:
            return rowIndex + 1;

          case PRIORITY_COLUMN:
            ChoicePanel cp = (modelData.get(rowIndex));
            Rule rule = cp.getChoice().getChoiceObject();
            return new Priority(rule.getPriority());

          case DESCRIPTION_COLUMN:
            return list.get(rowIndex).toString();
        }
        return null;
      }

      public String getColumnName(int column) {
        switch (column) {
          case SEQUENCE_COLUMN:
            return "Seq";

          case PRIORITY_COLUMN:
            return "Pri";

          case DESCRIPTION_COLUMN:
            return "Rule";
        }
        return null;
      }

      public Class getColumnClass(int columnIndex) {
        switch (columnIndex) {
          case SEQUENCE_COLUMN:
            return Integer.class;

          case PRIORITY_COLUMN:
            return Priority.class;

          case DESCRIPTION_COLUMN:
            return String.class;
        }
        return Object.class;
      }

      public boolean isCellEditable(int rowIndex, int columnIndex) {
        if (columnIndex == PRIORITY_COLUMN && rowIndex >= 0) {
          ChoicePanel cp = (modelData.get(rowIndex));
          Rule rule = cp.getChoice().getChoiceObject();
          if (rule instanceof PrioritizedRule) {
            return true;
          }
        }
        return false;
      }

      public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == PRIORITY_COLUMN) {
          ChoicePanel cp = (modelData.get(rowIndex));
          Rule rule = cp.getChoice().getChoiceObject();
          if (rule instanceof PrioritizedRule) {
            int priority;
            try {
              priority = (new Integer((String)aValue));
            }
            catch (NumberFormatException nfe) {
              return;
            }
            if (priority > 0 && priority < 100) {
              rule.setPriority(priority);
              fireTableCellUpdated(rowIndex, columnIndex);
            }
          }
        }
      }
    };
    TableCellRenderer priorityRenderer = new DefaultTableCellRenderer() {
      protected void setValue(Object value) {
        Priority p = (Priority)value;
        if (p.priority <= 0) {
          setText("n/a");
          setHorizontalAlignment(JLabel.CENTER);
        }
        else {
          setText("" + p.priority + " ");
          setHorizontalAlignment(JLabel.RIGHT);
        }
      }
    };
    TableSorter sorter = new TableSorter(model) {
      public void sortByColumn(int column, boolean ascending) {
        if (column == SEQUENCE_COLUMN ||
            column == PRIORITY_COLUMN)
        {
          super.sortByColumn(column, ascending);
        }
      }

      public boolean defaultSortOrderAscending(int column) {
        switch (column) {
          case SEQUENCE_COLUMN:
            return true;
          case PRIORITY_COLUMN:
            return false;
        }
        return true;
      }
    };
    jTable = new JTable(sorter);
    sorter.addMouseListenerToHeaderInTable(jTable);
    jTable.setShowHorizontalLines(false);
    jTable.setShowVerticalLines(true);
    jTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    jTable.setDragEnabled(false);
    jTable.setRowSelectionAllowed(true);
    jTable.setColumnSelectionAllowed(false);
    jTable.setPreferredScrollableViewportSize(jTable.getPreferredSize());
    jTable.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);

    TableColumnModel tcm = jTable.getColumnModel();
    tcm.getColumn(0).setPreferredWidth(new JLabel("SEQ").getWidth() * 3);
    tcm.getColumn(1).setPreferredWidth(new JLabel("MMM").getWidth() * 3);
    tcm.getColumn(1).setCellRenderer(priorityRenderer);
    tcm.getColumn(1).setCellEditor(priorityEditor);
    final JScrollPane scrollPane = new JScrollPane(jTable);
    for (AttributeGroup ca : list) {
      modelData.add(createOptionsPaneMsg(ca));
    }
    if (list.size() > 0) {
      jTable.getSelectionModel().setSelectionInterval(0, 0);
    }
    cmlPanel.add(scrollPane, constraints.weightedLastCol());
    setTableColumnWidths();
    return cmlPanel;
  }

  /**
   * @param ca FieldAttributes, MethodAttributes, InnerClassAttributes, or CommentRule object
   * @return ItemPanel corresponding to the value of param.
   */
  private ChoicePanel createOptionsPaneMsg(final AttributeGroup ca) {
    final ChoicePanel result = createChoicePanel();
    for (int i = 0; i < result.getChoices().length; i++) {
      final AttributeGroup choiceObject = result.getChoices()[i].getChoiceObject();
      if (ca.getClass() == choiceObject.getClass()) {
        result.setChoice(i);
        result.getChoice().choiceObject = ca;
        break;
      }
    }
    return result;
  }

  abstract ChoicePanel createChoicePanel();

  private void setTableColumnWidths() {
    int columnCount = jTable.getColumnCount();
    TableColumnModel tcm = jTable.getColumnModel();
    final int SPARE = 24;

    for (int i = 0; i < columnCount; i++) {
      int width = calculateColumnWidth(i);
      width += SPARE;
      TableColumn column = tcm.getColumn(i);
      column.setMinWidth(width);
      if (i < columnCount - 1) {
        column.setMaxWidth(width);
      }
    }
  }

  private int calculateColumnWidth(int columnIndex) {
    int result = 0;
    int rowCount = jTable.getRowCount();
    for (int i = 0; i < rowCount; i++) {
      TableCellRenderer tcr = jTable.getCellRenderer(i, columnIndex);
      Component comp = tcr.getTableCellRendererComponent(
        jTable,
        jTable.getValueAt(i, columnIndex),
        true, true, i, columnIndex
      );
      int compWidth = comp.getPreferredSize().width;
      if (compWidth > result) {
        result = compWidth;
      }
    }
    TableCellRenderer tcr = jTable.getColumnModel().getColumn(columnIndex).getHeaderRenderer();
    if (tcr == null) {
      tcr = jTable.getDefaultRenderer(String.class);
    }
    Component comp = tcr.getTableCellRendererComponent(
      jTable,
      model.getColumnName(columnIndex), true, true, -1, columnIndex
    );
    int compWidth = comp.getPreferredSize().width;
    if (compWidth > result) {
      result = compWidth;
    }
    if (columnIndex == DESCRIPTION_COLUMN) {
      if (result < 400) result = 400;
    }
    return result;
  }

  private JPanel createAddRemovePanel() {
    final JPanel addRemovePanel = new JPanel(new GridBagLayout());
    final Constraints constraints = new Constraints(GridBagConstraints.NORTH);
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets = new Insets(0, 0, 10, 0);
    final JButton addButton = new JButton("Add");
    final JButton editButton = new JButton("Edit");
    final JButton removeButton = new JButton("Remove");
    final JButton duplicateButton = new JButton("Duplicate");
    final JButton moveUpButton = new JButton("Move up");
    final JButton moveDownButton = new JButton("Move down");
    addRemovePanel.add(addButton, constraints.firstCol());
    constraints.newRow();
    addRemovePanel.add(editButton, constraints.firstCol());
    constraints.newRow();
    addRemovePanel.add(duplicateButton, constraints.firstCol());
    constraints.newRow();
    addRemovePanel.add(removeButton, constraints.firstCol());
    constraints.newRow();
    addRemovePanel.add(moveUpButton, constraints.firstCol());
    constraints.weightedNewRow();
    constraints.insets = new Insets(0, 0, 0, 0);
    addRemovePanel.add(moveDownButton, constraints.firstCol());
    if (jTable.getSelectedRow() < 0) {
      editButton.setEnabled(false);
      duplicateButton.setEnabled(false);
      removeButton.setEnabled(false);
      moveUpButton.setEnabled(false);
      moveDownButton.setEnabled(false);
    }
    if (jTable.getSelectedRow() == 0) {
      moveUpButton.setEnabled(false);
    }
    if (jTable.getSelectedRowCount() > 0 &&
        jTable.getSelectedRows()[jTable.getSelectedRowCount() - 1] == jTable.getModel().getRowCount() - 1)
    {
      moveDownButton.setEnabled(false);
    }
    jTable.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(final ListSelectionEvent e) {
          if (e.getValueIsAdjusting() || (e.getFirstIndex() < 0)) {
            return;
          }
          final int index = jTable.getSelectedRow();
          final boolean selectionExists = index >= 0;
          editButton.setEnabled(selectionExists);
          duplicateButton.setEnabled(selectionExists);
          removeButton.setEnabled(selectionExists);
          moveUpButton.setEnabled(selectionExists && index > 0);
          int lastSelectedRow = 0;
          if (selectionExists) {
            lastSelectedRow = jTable.getSelectedRows()[jTable.getSelectedRowCount() - 1];
          }
          moveDownButton.setEnabled(selectionExists &&
                                    lastSelectedRow < jTable.getModel().getRowCount() - 1);
        }
      }
    );
    jTable.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          int index = jTable.getSelectedRow();
          editRow(addRemovePanel);
          jTable.getSelectionModel().setLeadSelectionIndex(index);
        }
      }
    });
    addButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          // add new rule after last selected rule
          final int currentIndex;
          if (jTable.getSelectedRowCount() <= 0) {
            currentIndex = 0;
          }
          else {
            currentIndex = jTable.getSelectedRows()[jTable.getSelectedRowCount() - 1] + 1;
          }
          final ChoicePanel choicePanel = getOptionsPaneMsg(null);
          final JOptionPane op = new JOptionPane(
            choicePanel,
            JOptionPane.PLAIN_MESSAGE,
            JOptionPane.OK_CANCEL_OPTION,
            null, null, null
          );
          final JDialog jd = op.createDialog(addRemovePanel, choicePanel.getChoicePanelName());
          jd.setVisible(true);
          final Object result = op.getValue();
          if (result != null &&
              ((Integer)result) == JOptionPane.OK_OPTION)
          {
            final ChoicePanel value = (ChoicePanel)op.getMessage();
            value.acceptEdited();
            modelData.add(currentIndex, value);
            list.add(currentIndex, ((com.wrq.rearranger.settings.attributeGroups.AttributeGroup)value.getChoice().getChoiceObject()));
            model.fireTableRowsInserted(currentIndex, currentIndex);
            jTable.getSelectionModel().setSelectionInterval(currentIndex, currentIndex);
          }
          setTableColumnWidths();
        }
      }
    );
    editButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          editRow(addRemovePanel);
        }
      }
    );
    jTable.addMouseListener(
      new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() >= 2) {
            editRow(addRemovePanel);
          }
        }
      }
    );

    duplicateButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          // duplicate all selected rows as a block
          AttributeGroup[] newRules = new AttributeGroup[jTable.getSelectedRowCount()];
          for (int row = 0; row < jTable.getSelectedRowCount(); row++) {
            final int currentIndex = jTable.getSelectedRows()[row];
            final ChoicePanel choicePanel = (modelData.get(currentIndex));
            AttributeGroup rule = choicePanel.getChoice().getChoiceObject();
            AttributeGroup newRule = rule.deepCopy();
            newRules[row] = newRule;
          }
          // now insert the new rows at the correct place
          final int offset = jTable.getSelectedRows()[jTable.getSelectedRowCount() - 1] + 1;
          for (int row = 0; row < newRules.length; row++) {
            final AttributeGroup newRule = newRules[row];
            modelData.add(row + offset, createOptionsPaneMsg(newRule));
            list.add(row + offset, newRule);
            model.fireTableRowsInserted(row + offset, row + offset);
          }
        }
      }
    );
    removeButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          // remove a block of rules.
          final int currentIndex = jTable.getSelectedRow();
          if (currentIndex >= 0) {
            final int first = jTable.getSelectedRows()[0];
            final int last = jTable.getSelectedRows()[jTable.getSelectedRowCount() - 1];
            for (int i = first; i <= last; i++) {
              list.remove(currentIndex);
              modelData.remove(currentIndex);
            }
            model.fireTableRowsDeleted(currentIndex, currentIndex);
            int newSelIndex = (currentIndex >= model.getRowCount())
                              ? model.getRowCount() - 1
                              : currentIndex;
            jTable.getSelectionModel().setSelectionInterval(newSelIndex, newSelIndex);
            setTableColumnWidths();
          }
        }
      }
    );
    moveUpButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          final int first = jTable.getSelectedRows()[0];
          if (first <= 0) return;
          final int last = jTable.getSelectedRows()[jTable.getSelectedRowCount() - 1];
          for (int i = first; i <= last; i++) {
            list.add(i - 1, list.remove(i));
            modelData.add(i - 1, modelData.remove(i));
            model.fireTableRowsUpdated(i - 1, i);
          }
          jTable.getSelectionModel().setSelectionInterval(first - 1, last - 1);
        }
      }
    );
    moveDownButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          final int first = jTable.getSelectedRows()[0];
          final int last = jTable.getSelectedRows()[jTable.getSelectedRowCount() - 1];
          if (last + 1 >= jTable.getModel().getRowCount()) return;
          for (int i = last; i >= first; i--) {
            list.add(i + 1, (list.remove(i)));
            modelData.add(i + 1, (modelData.remove(i)));
            model.fireTableRowsUpdated(i, i + 1);
          }
          jTable.getSelectionModel().setSelectionInterval(first + 1, last + 1);
        }
      }
    );
    return addRemovePanel;
  }

  private ChoicePanel getOptionsPaneMsg(final ChoicePanel currentMsg) {
    final ChoicePanel result = createChoicePanel();
    if (currentMsg != null) {
      result.copyChoices(currentMsg);
    }
    result.configurePanel();
    return result;
  }

  private void editRow(final JPanel addRemovePanel) {
    // edit first row of all rows selected
    final int currentIndex = jTable.getSelectedRow();
    final ChoicePanel o = modelData.get(currentIndex);
    final ChoicePanel choicePanel = getOptionsPaneMsg(o);
    final JOptionPane op = new JOptionPane(
      choicePanel,
      JOptionPane.PLAIN_MESSAGE,
      JOptionPane.OK_CANCEL_OPTION,
      null, null, null
    );
    final JDialog jd = op.createDialog(addRemovePanel, choicePanel.getChoicePanelName());
    jd.setVisible(true);
    final Object result = op.getValue();
    if (result != null &&
        ((Integer)result) == JOptionPane.OK_OPTION)
    {
      final ChoicePanel value = (ChoicePanel)op.getMessage();
      value.acceptEdited();
      modelData.set(currentIndex, value);
      list.set(currentIndex, ((com.wrq.rearranger.settings.attributeGroups.AttributeGroup)value.getChoice().getChoiceObject()));
      model.fireTableRowsUpdated(currentIndex, currentIndex);
    }
    setTableColumnWidths();
  }

// --------------------- GETTER / SETTER METHODS ---------------------

  public final JPanel getPane() {
    return pane;
  }

// -------------------------- INNER CLASSES --------------------------

  class Priority {
    int priority;

    public Priority(int priority) {
      this.priority = priority;
    }

    public String toString() {
      return "" + priority;
    }
  }
}
