// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.quickFixes;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.ui.ClickListener;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * [vova] This class should be inner but due to bugs in "beta" generics compiler
 * I need to use "static" modifier.
 */
final class LightBulbComponentImpl extends JComponent{
  private final QuickFixManager myManager;
  private final Icon myIcon;

  LightBulbComponentImpl(final @NotNull QuickFixManager manager, final @NotNull Icon icon) {
    myManager = manager;
    myIcon = icon;

    setPreferredSize(new Dimension(icon.getIconWidth(), icon.getIconHeight()));
    final String acceleratorsText = KeymapUtil.getFirstKeyboardShortcutText(
      ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));
    if (acceleratorsText.length() > 0) {
      setToolTipText(UIDesignerBundle.message("tooltip.press.accelerator", acceleratorsText));
    }

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        myManager.showIntentionPopup();
        return true;
      }
    }.installOn(this);
  }

  @Override
  protected void paintComponent(final Graphics g) {
    myIcon.paintIcon(this, g, 0, 0);
  }
}
