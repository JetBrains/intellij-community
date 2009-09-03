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
