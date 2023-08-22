// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.laf.macos;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import static com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.isSmallVariant;
import static com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.isTag;

/**
 * @author Konstantin Bulenkov
 */
public final class MacIntelliJButtonBorder implements Border, UIResource {
  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    if (!c.hasFocus() || c instanceof JComponent && UIUtil.isHelpButton(c)) return;

    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.translate(x, y);

      float arc = MacIntelliJTextBorder.ARC.getFloat();

      if (isSmallVariant(c) && c.isFocusable() && c.hasFocus()) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                            MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

        float f = UIUtil.isRetina(g2) ? 0.5f : 1.0f;
        float lw = JBUIScale.scale(f);

        Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        border.append(new RoundRectangle2D.Float(0, 0, width, height, arc + lw, arc + lw), false);
        border.append(new RoundRectangle2D.Float(lw * 2, lw * 2, width - lw * 4, height - lw * 4, arc, arc), false);

        g2.setColor(JBUI.CurrentTheme.Focus.focusColor());
        g2.fill(border);
      }
      else if (isTag(c)) {
        DarculaUIUtil.paintTag(g2, width, height, c.hasFocus(), DarculaUIUtil.computeOutlineFor(c));
      }
      else {
        DarculaUIUtil.paintFocusBorder(g2, width, height, arc, true);
      }
    }
    finally {
      g2.dispose();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return isSmallVariant(c) ? JBUI.insets(1, 2).asUIResource() : JBUI.insets(3).asUIResource();
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
