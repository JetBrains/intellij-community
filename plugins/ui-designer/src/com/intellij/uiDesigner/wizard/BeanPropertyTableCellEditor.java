// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.wizard;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;

final class BeanPropertyTableCellEditor extends AbstractCellEditor implements TableCellEditor{
  private final JTextField myEditorComponent;

  BeanPropertyTableCellEditor() {
    myEditorComponent = new JTextField();
    myEditorComponent.setBorder(null);
  }

  @Override
  public Object getCellEditorValue() {
    final String propertyName = myEditorComponent.getText().trim();
    if(propertyName.length() != 0){
      return new BeanProperty(propertyName, "java.lang.String"/*TODO[vova] provide real implementation*/);
    }
    else{
      return null;
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
    final BeanProperty property = (BeanProperty)value;

    if(property != null){
      myEditorComponent.setText(property.myName);
    }
    else{
      myEditorComponent.setText(null);
    }

    return myEditorComponent;
  }
}
