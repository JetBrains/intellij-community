
package com.intellij.refactoring.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiVariable;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

public abstract class ParameterTablePanel extends JPanel{
  private final Project myProject;
  private final VariableData[] myVariableData;

  private final Table myTable;
  private MyTableModel myTableModel;
  private JButton myUpButton;
  private JButton myDownButton;

  public static class VariableData {
    public final PsiVariable variable;
    public String name;
    public boolean passAsParameter;

    public VariableData(PsiVariable variable) {
      this.variable = variable;
    }
  }

  protected abstract void updateSignature();
  protected abstract void doEnterAction();
  protected abstract void doCancelAction();

  public ParameterTablePanel(Project project, VariableData[] variableData) {
    super(new BorderLayout());
    myProject = project;
    myVariableData = variableData;

    myTableModel = new MyTableModel();
    myTable = new Table(myTableModel);
    DefaultCellEditor defaultEditor=(DefaultCellEditor)myTable.getDefaultEditor(Object.class);
    defaultEditor.setClickCountToStart(1);

    myTable.setTableHeader(null);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.getColumnModel().getColumn(myTableModel.CHECKMARK_COLUMN).setCellRenderer(new CheckBoxTableCellRenderer());
    myTable.getColumnModel().getColumn(myTableModel.CHECKMARK_COLUMN).setMaxWidth(new JCheckBox().getPreferredSize().width);
    myTable.getColumnModel().getColumn(myTableModel.PARAMETER_COLUMN).setCellRenderer(new ParameterTableCellRenderer());
    myTable.setPreferredScrollableViewportSize(new Dimension(250, myTable.getRowHeight() * 5));
    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    myTable.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enable_disable");
    myTable.getActionMap().put("enable_disable", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (myTable.isEditing()) return;
        int[] rows = myTable.getSelectedRows();
        if (rows.length > 0){
          boolean valueToBeSet = false;
          for(int idx = 0; idx < rows.length; idx++){
            if (!myVariableData[rows[idx]].passAsParameter){
              valueToBeSet = true;
              break;
            }
          }
          for(int idx = 0; idx < rows.length; idx++){
            myVariableData[rows[idx]].passAsParameter = valueToBeSet;
          }
          myTableModel.fireTableRowsUpdated(rows[0], rows[rows.length - 1]);
          TableUtil.selectRows(myTable, rows);
        }
      }
    });
    // F2 should edit the name
    myTable.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "edit_parameter_name");
    myTable.getActionMap().put("edit_parameter_name", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (!myTable.isEditing()){
          int row = myTable.getSelectedRow();
          if (row >=0 && row < myTableModel.getRowCount()) {
            TableUtil.editCellAt(myTable, row, myTableModel.PARAMETER_COLUMN);
          }
        }
      }
    });

    // make ENTER work when the table has focus
    myTable.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "invokeImpl");
    myTable.getActionMap().put("invokeImpl", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        TableCellEditor editor = myTable.getCellEditor();
        if (editor != null){
          editor.stopCellEditing();
        }
        else{
          doEnterAction();
        }
      }
    });

    // make ESCAPE work when the table has focus
    myTable.getActionMap().put("doCancel", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        TableCellEditor editor = myTable.getCellEditor();
        if (editor != null){
          editor.stopCellEditing();
        }
        else{
          doCancelAction();
        }
      }
    });

    JPanel listPanel = new JPanel(new BorderLayout());
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);
    listPanel.add(scrollPane, BorderLayout.CENTER);
    listPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    this.add(listPanel, BorderLayout.CENTER);

    JPanel buttonsPanel = new JPanel();
    buttonsPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    this.add(buttonsPanel, BorderLayout.EAST);

    buttonsPanel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.insets = new Insets(2, 4, 2, 4);

    myUpButton = new JButton("Move Up");
    myUpButton.setMnemonic('u');
    myUpButton.setDefaultCapable(false);
    buttonsPanel.add(myUpButton, gbConstraints);

    myDownButton = new JButton("Move Down");
    myDownButton.setMnemonic('d');
    myDownButton.setDefaultCapable(false);
    buttonsPanel.add(myDownButton, gbConstraints);

    gbConstraints.weighty = 1;
    buttonsPanel.add(new JPanel(), gbConstraints);

    myUpButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if(myTable.isEditing()) {
            final boolean isStopped = myTable.getCellEditor().stopCellEditing();
            if(!isStopped) return;
          }
          moveSelectedItem(-1);
          updateSignature();
          myTable.requestFocus();
        }
      }
    );

    myDownButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if(myTable.isEditing()) {
            final boolean isStopped = myTable.getCellEditor().stopCellEditing();
            if(!isStopped) return;
          }
          moveSelectedItem(+1);
          updateSignature();
          myTable.requestFocus();
        }
      }
    );

    myTable.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          updateMoveButtons();
        }
      }
    );
    if (myVariableData.length <= 1) {
      myUpButton.setEnabled(false);
      myDownButton.setEnabled(false);
    }
    else {
      myTable.getSelectionModel().setSelectionInterval(0, 0);
    }
    updateMoveButtons();
  }

  private void updateMoveButtons() {
    int row = myTable.getSelectedRow();
    if (0 <= row && row < myVariableData.length){
      myUpButton.setEnabled(row > 0);
      myDownButton.setEnabled(row < myVariableData.length - 1);
    }
    else{
      myUpButton.setEnabled(false);
      myDownButton.setEnabled(false);
    }
  }

  private void moveSelectedItem(int moveIncrement) {
    int row = myTable.getSelectedRow();
    if (row < 0 || row >= myVariableData.length) return;
    int targetRow = row + moveIncrement;
    if (targetRow < 0 || targetRow >= myVariableData.length) return;
    VariableData currentItem = myVariableData[row];
    myVariableData[row] = myVariableData[targetRow];
    myVariableData[targetRow] = currentItem;
    myTableModel.fireTableRowsUpdated(Math.min(targetRow, row), Math.max(targetRow, row));
    myTable.getSelectionModel().setSelectionInterval(targetRow, targetRow);
  }

  public void setEnabled(boolean enabled) {
    myTable.setEnabled(enabled);
    if (!enabled) {
        myUpButton.setEnabled(false);
        myDownButton.setEnabled(false);
    }
    else {
      updateMoveButtons();
    }
    super.setEnabled(enabled);
  }

  private class MyTableModel extends AbstractTableModel {
    public final int CHECKMARK_COLUMN = 0;
    public final int PARAMETER_COLUMN = 1;

    public int getRowCount() {
      return myVariableData.length;
    }

    public int getColumnCount() {
      return 2;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECKMARK_COLUMN : {
          return myVariableData[rowIndex].passAsParameter? Boolean.TRUE : Boolean.FALSE;
        }
        case PARAMETER_COLUMN : {
          return myVariableData[rowIndex].name;
        }
      }
      return null;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECKMARK_COLUMN : {
          myVariableData[rowIndex].passAsParameter = ((Boolean)aValue).booleanValue();
          fireTableRowsUpdated(rowIndex, rowIndex);
          myTable.getSelectionModel().setSelectionInterval(rowIndex, rowIndex);
          updateSignature();
          break;
        }
        case PARAMETER_COLUMN : {
          VariableData data = myVariableData[rowIndex];
          String name = (String)aValue;
          if (PsiManager.getInstance(myProject).getNameHelper().isIdentifier(name)) {
            data.name = name;
          }
          updateSignature();
          break;
        }
      }
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECKMARK_COLUMN :
          return ParameterTablePanel.this.isEnabled();
        case PARAMETER_COLUMN :
          return ParameterTablePanel.this.isEnabled() && myVariableData[rowIndex].passAsParameter;
        default:
          return false;
      }
    }

    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECKMARK_COLUMN) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }
  }

  private class CheckBoxTableCellRenderer extends BooleanTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
      Component rendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      rendererComponent.setEnabled(ParameterTablePanel.this.isEnabled());
      return rendererComponent;
    }
  }
  private class ParameterTableCellRenderer extends DefaultTableCellRenderer {
    public Component getTableCellRendererComponent(
      JTable table,
      Object value,
      boolean isSelected,
      boolean hasFocus,
      int row,
      int column
    ) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      VariableData data = myVariableData[row];
      String type = data.variable.getType().getPresentableText();
      setText(type+" "+data.name);
      return this;
    }
  }
}