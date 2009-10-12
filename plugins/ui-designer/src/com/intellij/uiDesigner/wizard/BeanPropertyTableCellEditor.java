/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.wizard;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class BeanPropertyTableCellEditor extends AbstractCellEditor implements TableCellEditor{
  private final JTextField myEditorComponent;

  public BeanPropertyTableCellEditor() {
    myEditorComponent = new JTextField();
    myEditorComponent.setBorder(null);
  }

  public Object getCellEditorValue() {
    final String propertyName = myEditorComponent.getText().trim();
    if(propertyName.length() != 0){
      return new BeanProperty(propertyName, "java.lang.String"/*TODO[vova] provide real implementation*/);
    }
    else{
      return null;
    }
  }

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
