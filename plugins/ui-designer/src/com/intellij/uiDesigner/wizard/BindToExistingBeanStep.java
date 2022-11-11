// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.wizard;

import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;

final class BindToExistingBeanStep extends StepAdapter{
  private static final Logger LOG = Logger.getInstance(BindToExistingBeanStep.class);

  private JScrollPane myScrollPane;
  private JTable myTable;
  private final WizardData myData;
  private final MyTableModel myTableModel;
  private JCheckBox myChkIsModified;
  private JCheckBox myChkGetData;
  private JCheckBox myChkSetData;
  private JPanel myPanel;

  BindToExistingBeanStep(@NotNull final WizardData data) {
    myData = data;
    myTableModel = new MyTableModel();
    myTable.setModel(myTableModel);
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.getColumnModel().setColumnSelectionAllowed(true);
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
      final MyTableCellEditor cellEditor = new MyTableCellEditor();
      column.setCellEditor(cellEditor);

      final DefaultCellEditor editor = (DefaultCellEditor)myTable.getDefaultEditor(Object.class);
      editor.setClickCountToStart(1);

      myTable.setRowHeight(cellEditor.myCbx.getPreferredSize().height);
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
    LOG.assertTrue(!myData.myBindToNewBean);
    LOG.assertTrue(myData.myBeanClass != null);
    myTableModel.fireTableDataChanged();
  }

  @Override
  public void _commit(boolean finishChosen) {
    // Stop editing if any
    final TableCellEditor cellEditor = myTable.getCellEditor();
    if(cellEditor != null){
      cellEditor.stopCellEditing();
    }

    myData.myGenerateIsModified = myChkIsModified.isSelected();

    // TODO[vova] check that at least one binding field exists
  }

  private final class MyTableModel extends AbstractTableModel{
    private final String[] myColumnNames;

    MyTableModel() {
      myColumnNames = new String[]{
        UIDesignerBundle.message("column.form.field"),
        UIDesignerBundle.message("column.bean.property")};
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
    public int getRowCount() {
      return myData.myBindings.length;
    }

    @Override
    public boolean isCellEditable(final int row, final int column) {
      return column == 1/*Bean Property*/;
    }

    @Override
    public Object getValueAt(final int row, final int column) {
      if(column == 0/*Form Property*/){
        return myData.myBindings[row].myFormProperty;
      }
      else if(column == 1/*Bean Property*/){
        return myData.myBindings[row].myBeanProperty;
      }
      else{
        throw new IllegalArgumentException("unknown column: " + column);
      }
    }

    @Override
    public void setValueAt(final Object value, final int row, final int column) {
      LOG.assertTrue(column == 1/*Bean Property*/);
      final FormProperty2BeanProperty binding = myData.myBindings[row];
      binding.myBeanProperty = (BeanProperty)value;
    }
  }

  private final class MyTableCellEditor extends AbstractCellEditor implements TableCellEditor{
    private final ComboBox myCbx;
    /* -1 if not defined*/
    private int myEditingRow;

    MyTableCellEditor() {
      myCbx = new ComboBox();
      myCbx.setEditable(true);
      myCbx.setRenderer(new BeanPropertyListCellRenderer());
      myCbx.registerTableCellEditor(this);

      final JComponent editorComponent = (JComponent)myCbx.getEditor().getEditorComponent();
      editorComponent.setBorder(null);

      myEditingRow = -1;
    }

    /**
     * @return whether it's possible to convert {@code type1} into {@code type2}
     * and vice versa.
     */
    private boolean canConvert(@NonNls final String type1, @NonNls final String type2){
      if("boolean".equals(type1) || "boolean".equals(type2)){
        return type1.equals(type2);
      }
      else{
        return true;
      }
    }

    @Override
    public Component getTableCellEditorComponent(
      final JTable table,
      final Object value,
      final boolean isSelected,
      final int row,
      final int column
    ) {
      myEditingRow = row;
      final DefaultComboBoxModel model = (DefaultComboBoxModel)myCbx.getModel();
      model.removeAllElements();
      model.addElement(null/*<not defined>*/);

      // Fill combobox with available bean's properties
      final String[] rProps = PropertyUtilBase.getReadableProperties(myData.myBeanClass, true);
      final String[] wProps = PropertyUtilBase.getWritableProperties(myData.myBeanClass, true);
      final ArrayList<BeanProperty> rwProps = new ArrayList<>();

      outer: for(int i = rProps.length - 1; i >= 0; i--){
        final String propName = rProps[i];
        if(ArrayUtil.find(wProps, propName) != -1){
          LOG.assertTrue(rwProps.stream().noneMatch(bp -> bp.myName.equals(propName)));
          final PsiMethod getter = PropertyUtilBase.findPropertyGetter(myData.myBeanClass, propName, false, true);
          if (getter == null) {
            // possible if the getter is static: getReadableProperties() does not filter out static methods, and
            // findPropertyGetter() checks for static/non-static
            continue;
          }
          final PsiType returnType = getter.getReturnType();
          LOG.assertTrue(returnType != null);

          // There are two possible types: boolean and java.lang.String
          @NonNls final String typeName = returnType.getCanonicalText();
          if(!"boolean".equals(typeName) && !"java.lang.String".equals(typeName)){
            continue;
          }

          // Check that the property is not in use yet
          for(int j = myData.myBindings.length - 1; j >= 0; j--){
            final BeanProperty _property = myData.myBindings[j].myBeanProperty;
            if(j != row && _property != null && propName.equals(_property.myName)){
              continue outer;
            }
          }

          // Check that we conver types
          if(
            !canConvert(
              myData.myBindings[row].myFormProperty.getComponentPropertyClassName(),
              typeName
            )
          ){
            continue;
          }

          rwProps.add(new BeanProperty(propName, typeName));
        }
      }

      Collections.sort(rwProps);

      for (BeanProperty rwProp : rwProps) {
        model.addElement(rwProp);
      }

      // Set initially selected item
      if(myData.myBindings[row].myBeanProperty != null){
        myCbx.setSelectedItem(myData.myBindings[row].myBeanProperty);
      }
      else{
        myCbx.setSelectedIndex(0/*<not defined>*/);
      }

      return myCbx;
    }

    @Override
    public Object getCellEditorValue() {
      LOG.assertTrue(myEditingRow != -1);
      try {
        // our ComboBox is editable so its editor can contain:
        // 1) BeanProperty object (it user just selected something from ComboBox)
        // 2) java.lang.String if user type something into ComboBox

        final Object selectedItem = myCbx.getEditor().getItem();
        if(selectedItem instanceof BeanProperty){
          return selectedItem;
        }
        else if(selectedItem instanceof String){
          final String fieldName = ((String)selectedItem).trim();

          if(fieldName.length() == 0){
            return null; // binding is not defined
          }

          final String fieldType = myData.myBindings[myEditingRow].myFormProperty.getComponentPropertyClassName();
          return new BeanProperty(fieldName, fieldType);
        }
        else{
          throw new IllegalArgumentException("unknown selectedItem: " + selectedItem);
        }
      }
      finally {
        myEditingRow = -1; // unset editing row. So it's possible to invoke this method only once per editing
      }
    }
  }
}
