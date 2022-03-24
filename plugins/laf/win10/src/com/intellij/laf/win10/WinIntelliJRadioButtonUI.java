// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.laf.win10;

import com.intellij.ide.ui.laf.darcula.ui.DarculaRadioButtonUI;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBInsets;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public final class WinIntelliJRadioButtonUI extends DarculaRadioButtonUI {
  private static final Icon DEFAULT_ICON = JBUIScale.scaleIcon(EmptyIcon.create(13)).asUIResource();

  @Override
  protected Rectangle updateViewRect(AbstractButton b, Rectangle viewRect) {
    JBInsets.removeFrom(viewRect, b.getInsets());
    return viewRect;
  }

  @Override
  protected Dimension computeOurPreferredSize(JComponent c) {
    return null;
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    AbstractButton b = (AbstractButton)c;
    b.setRolloverEnabled(true);
    return new WinIntelliJRadioButtonUI();
  }

  @Override
  protected void paintIcon(JComponent c, Graphics2D g, Rectangle viewRect, Rectangle iconRect) {
    AbstractButton b = (AbstractButton)c;
    ButtonModel bm = b.getModel();
    boolean focused = c.hasFocus() || bm.isRollover();
    Icon icon = WinIconLookup.getIcon("radio", bm.isSelected(), focused, bm.isEnabled(), false, bm.isPressed());
    icon.paintIcon(c, g, iconRect.x, iconRect.y);
  }

  @Override
  public Icon getDefaultIcon() {
    return DEFAULT_ICON;
  }

  @Override
  protected int getMnemonicIndex(AbstractButton b) {
    return b.getDisplayedMnemonicIndex();
  }
}
