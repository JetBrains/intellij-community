/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.laf.win10;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

import static com.intellij.laf.win10.WinIntelliJTextFieldUI.HOVER_PROPERTY;

/**
 * @author Konstantin Bulenkov
 */
public final class WinIntelliJTextBorder extends DarculaTextBorder {
  static final JBValue MINIMUM_HEIGHT = new JBValue.Float(22);

  @Override
  public Insets getBorderInsets(Component c) {
    return DarculaUIUtil.isTableCellEditor(c) || DarculaUIUtil.isCompact(c) ?
           JBUI.insets(1, 1, 1, 4).asUIResource() :
           JBUI.insets(1).asUIResource();
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    JComponent jc = (JComponent)c;
    if (jc.getClientProperty("JTextField.Search.noBorderRing") == Boolean.TRUE) return;

    Graphics2D g2 = (Graphics2D)g.create();
    try {
      Rectangle r = new Rectangle(x, y, width, height);
      WinIntelliJTextFieldUI.adjustInWrapperRect(r, c);

      boolean isCellRenderer = DarculaUIUtil.isTableCellEditor(c);
      int bw = 1;
      Object op = jc.getClientProperty("JComponent.outline");
      if (c.isEnabled() && op != null) {
        DarculaUIUtil.Outline.valueOf(op.toString()).setGraphicsColor(g2, c.hasFocus());
        bw = isCellRenderer ? 1 : 2;
      }
      else {
        if (c.hasFocus()) {
          g2.setColor(UIManager.getColor("TextField.focusedBorderColor"));
        }
        else if (jc.getClientProperty(HOVER_PROPERTY) == Boolean.TRUE) {
          g2.setColor(UIManager.getColor("TextField.hoverBorderColor"));
        }
        else {
          g2.setColor(UIManager.getColor(c.isEnabled() ? "TextField.borderColor" : "Button.intellij.native.borderColor"));
        }

        if (!c.isEnabled()) {
          g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.47f));
        }

        if (!isCellRenderer) {
          JBInsets.removeFrom(r, JBUI.insets(1));
        }
      }

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      border.append(r, false);

      Rectangle innerRect = new Rectangle(r);
      JBInsets.removeFrom(innerRect, JBUI.insets(bw));
      border.append(innerRect, false);

      g2.fill(border);
    }
    finally {
      g2.dispose();
    }
  }
}

