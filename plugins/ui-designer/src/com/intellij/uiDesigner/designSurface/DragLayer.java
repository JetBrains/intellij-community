package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.radComponents.RadComponent;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class DragLayer extends PassiveDecorationLayer {
  public DragLayer(final GuiEditor editor) {
    super(editor);
  }

  public void paint(final Graphics g){
    ((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.3f));

    // Paint passive decoration of dragged components
    for(int i = getComponentCount() - 1; i >= 0; i--){
      final Component child = getComponent(i);
      if(child instanceof JComponent){
        final Object prop = ((JComponent)child).getClientProperty(RadComponent.CLIENT_PROP_RAD_COMPONENT);
        if(prop != null){
          final RadComponent radComponent = (RadComponent)prop;
          paintPassiveDecoration(radComponent, g);
        }
      }
    }

    // Paint selection rectangle
    paintChildren(g);
  }
}
