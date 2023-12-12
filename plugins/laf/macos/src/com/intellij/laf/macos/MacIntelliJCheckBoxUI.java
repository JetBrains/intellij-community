// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.laf.macos;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.DarculaCheckBoxUI;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.MacUIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public final class MacIntelliJCheckBoxUI extends DarculaCheckBoxUI {

  public MacIntelliJCheckBoxUI(JCheckBox c) {
    c.setOpaque(false);
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJCheckBoxUI(((JCheckBox)c));
  }

  @Override
  protected void drawCheckIcon(JComponent c, Graphics2D g, AbstractButton b, Rectangle iconRect, boolean selected, boolean enabled) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      String iconName = isIndeterminate(b) ? "checkBoxIndeterminate" : "checkBox";

      DarculaUIUtil.Outline op = DarculaUIUtil.getOutline(b);
      boolean hasFocus = op == null && b.hasFocus();
      Icon icon = MacIconLookup.getIcon(iconName, selected || isIndeterminate(b), hasFocus, b.isEnabled());
      icon.paintIcon(b, g2, iconRect.x, iconRect.y);

      if (op != null) {
        op.setGraphicsColor(g2, b.hasFocus());
        Path2D outline = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        outline.append(new RoundRectangle2D.Float(
          iconRect.x + JBUIScale.scale(1), iconRect.y + JBUIScale.scale(1), JBUIScale.scale(20), JBUIScale.scale(20), JBUIScale.scale(12),
          JBUIScale.scale(12)), false);
        outline.append(new RoundRectangle2D.Float(
          iconRect.x + JBUIScale.scale(4.5f), iconRect.y + JBUIScale.scale(4.5f), JBUIScale.scale(13), JBUIScale.scale(13), JBUIScale.scale(5),
          JBUIScale.scale(5)), false);

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                            MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);
        g2.fill(outline);
      }
    }
    finally {
      g2.dispose();
    }
  }
}
