// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.wizard;

import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiNameHelper;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;

final class BindToNewBeanStep extends StepAdapter{
  private static final Logger LOG = Logger.getInstance(BindToNewBeanStep.class);

  private JScrollPane myScrollPane;
  private JTable myTable;
  private final WizardData myData;
  private final MyTableModel myTableModel;
  private JCheckBox myChkIsModified;
  private JCheckBox myChkSetData;
  private JCheckBox myChkGetData;
  private JPanel myPanel;

  BindToNewBeanStep(@NotNull final WizardData data) {
    myData = data;
    myTableModel = new MyTableModel();
    myTable.setModel(myTableModel);
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myScrollPane.getViewport().setBackground(myTable.getBackground());
    myTable.setSurrendersFocusOnKeystroke(true);

    // Customize "Form Property" column
    {
      final TableColumn column = myTable.getColumnModel().getColumn(0/*Form Property*/);
      column.setCellRenderer(new FormPropertyTableCellRenderer(myData.myProject));
    }

    // Customize "Bean Property" column
    {
      final TableColumn column = myTable.getColumnModel().getColumn(1/*Bean Property*/);
      column.setCellRenderer(new BeanPropertyTableCellRenderer());
      column.setCellEditor(new BeanPropertyTableCellEditor());

      final DefaultCellEditor editor = (DefaultCellEditor)myTable.getDefaultEditor(Object.class);
      editor.setClickCountToStart(1);
    }

    myChkGetData.setSelected(true);
    myChkGetData.setEnabled(false);
    myChkSetData.setSelected(true);
    myChkSetData.setEnabled(false);
    myChkIsModified.setSelected(myData.myGenerateIsModified);
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void _init() {
    // Check that data is correct
    LOG.assertTrue(myData.myBindToNewBean);
    myTableModel.fireTableDataChanged();
  }

  @Override
  public void _commit(boolean finishChosen) throws CommitStepException {
    // Stop editing if any
    final TableCellEditor cellEditor = myTable.getCellEditor();
    if(cellEditor != null){
      cellEditor.stopCellEditing();
    }

    // Check that all included fields are bound to valid bean properties
    final PsiNameHelper nameHelper = PsiNameHelper.getInstance(myData.myProject);
    for(int i = 0; i <myData.myBindings.length; i++){
      final FormProperty2BeanProperty binding = myData.myBindings[i];
      if(binding.myBeanProperty == null){
        continue;
      }

      if (!nameHelper.isIdentifier(binding.myBeanProperty.myName)){
        throw new CommitStepException(
          UIDesignerBundle.message("error.X.is.not.a.valid.property.name", binding.myBeanProperty.myName)
        );
      }
    }

    myData.myGenerateIsModified = myChkIsModified.isSelected();
  }

  private final class MyTableModel extends AbstractTableModel{
    private final String[] myColumnNames;
    private final Class[] myColumnClasses;

    MyTableModel() {
      myColumnNames = new String[]{
        UIDesignerBundle.message("column.form.field"),
        UIDesignerBundle.message("column.bean.property")};
      myColumnClasses = new Class[]{Object.class, Object.class};
    }

    @Override
    public int getColumnCount() {
      return myColumnNames.length;
    }

    @Override
    public String getColumnName(final int column) {
      return myColumnNames[column];
    }

    @Override
    public Class getColumnClass(final int column) {
      return myColumnClasses[column];
    }

    @Override
    public int getRowCount() {
      return myData.myBindings.length;
    }

    @Override
    public boolean isCellEditable(final int row, final int column) {
      return column == 1/*Bean Property*/;
    }

    @Override
    public Object getValueAt(final int row, final int column) {
      final FormProperty2BeanProperty binding = myData.myBindings[row];
      if(column == 0/*Form Property*/){
        return binding.myFormProperty;
      }
      else if(column == 1/*Bean Property*/){
        return binding.myBeanProperty;
      }
      else{
        throw new IllegalArgumentException("unknown column: " + column);
      }
    }

    @Override
    public void setValueAt(final Object value, final int row, final int column) {
      final FormProperty2BeanProperty binding = myData.myBindings[row];
      if(column == 1/*Bean Property*/){
        binding.myBeanProperty = (BeanProperty)value;
      }
      else{
        throw new IllegalArgumentException("unknown column: " + column);
      }
    }
  }
}
