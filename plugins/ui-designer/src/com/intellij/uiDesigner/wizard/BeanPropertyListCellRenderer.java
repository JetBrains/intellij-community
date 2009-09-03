package com.intellij.uiDesigner.wizard;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.uiDesigner.UIDesignerBundle;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class BeanPropertyListCellRenderer extends ColoredListCellRenderer{
  private final SimpleTextAttributes myAttrs1;
  private final SimpleTextAttributes myAttrs2;

  public BeanPropertyListCellRenderer() {
    myAttrs1 = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
    myAttrs2 = SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

  protected void customizeCellRenderer(
    final JList list,
    final Object value,
    final int index,
    final boolean selected,
    final boolean hasFocus
  ) {
    final BeanProperty property = (BeanProperty)value;
    if(property == null){
      append(UIDesignerBundle.message("property.not.defined"), myAttrs2);
    }
    else{
      append(property.myName, myAttrs1);
      append(" ", myAttrs1);
      append(property.myType, myAttrs2);
    }
  }
}
