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
package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.shared.BorderType;
import com.intellij.uiDesigner.propertyInspector.InplaceContext;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class BorderTypeEditor extends ComboBoxPropertyEditor<BorderType> {
  public BorderTypeEditor(){
    myCbx.setModel(new DefaultComboBoxModel(BorderType.getAllTypes()));
    myCbx.setRenderer(new MyListCellRenderer());
  }

  public JComponent getComponent(final RadComponent ignored, final BorderType value, final InplaceContext inplaceContext){
    myCbx.setSelectedItem(value);
    return myCbx;
  }

  private static final class MyListCellRenderer extends DefaultListCellRenderer{
    public Component getListCellRendererComponent(
      final JList list,
      final Object value,
      final int index,
      final boolean isSelected,
      final boolean cellHasFocus
    ){
      final BorderType type = value != null ? (BorderType)value : BorderType.NONE;
      return super.getListCellRendererComponent(list,type.getName(),index,isSelected,cellHasFocus);
    }
  }
}
