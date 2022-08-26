// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  LightBulbComponentImpl(@NotNull final QuickFixManager manager, @NotNull final Icon icon) {
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
