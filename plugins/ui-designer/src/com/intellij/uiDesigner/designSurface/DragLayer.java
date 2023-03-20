// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.radComponents.RadComponent;

import javax.swing.*;
import java.awt.*;

final class DragLayer extends PassiveDecorationLayer {
  DragLayer(final GuiEditor editor) {
    super(editor);
  }

  @Override
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
