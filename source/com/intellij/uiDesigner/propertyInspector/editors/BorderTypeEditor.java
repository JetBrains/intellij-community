package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.shared.BorderType;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class BorderTypeEditor extends ComboBoxPropertyEditor{
  public BorderTypeEditor(){
    myCbx.setModel(
      new DefaultComboBoxModel(
        new BorderType[]{
          BorderType.NONE,
          BorderType.BEVEL_LOWERED,
          BorderType.BEVEL_RAISED,
          BorderType.ETCHED
        }
      )
    );
    myCbx.setRenderer(new MyListCellRenderer());
  }

  public JComponent getComponent(final RadComponent ignored, final Object value, final boolean inplace){
    if(!(value instanceof BorderType)){
      throw new IllegalArgumentException("wrong value: "+value);
    }
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
      final BorderType type=(BorderType)value;
      return super.getListCellRendererComponent(list,type.getName(),index,isSelected,cellHasFocus);
    }
  }
}
