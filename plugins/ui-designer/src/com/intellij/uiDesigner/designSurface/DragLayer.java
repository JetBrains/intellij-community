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
