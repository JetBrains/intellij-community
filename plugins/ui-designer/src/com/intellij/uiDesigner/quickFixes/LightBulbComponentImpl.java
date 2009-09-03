package com.intellij.uiDesigner.quickFixes;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * [vova] This class should be inner but due to bugs in "beta" generics compiler
 * I need to use "static" modifier.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class LightBulbComponentImpl extends JComponent{
  private final QuickFixManager myManager;
  private final Icon myIcon;

  public LightBulbComponentImpl(@NotNull final QuickFixManager manager, @NotNull final Icon icon) {
    myManager = manager;
    myIcon = icon;

    setPreferredSize(new Dimension(icon.getIconWidth(), icon.getIconHeight()));
    final String acceleratorsText = KeymapUtil.getFirstKeyboardShortcutText(
      ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));
    if (acceleratorsText.length() > 0) {
      setToolTipText(UIDesignerBundle.message("tooltip.press.accelerator", acceleratorsText));
    }

    addMouseListener(
      new MouseAdapter() {
        public void mouseClicked(final MouseEvent e) {
          myManager.showIntentionPopup();
        }
      }
    );
  }

  protected void paintComponent(final Graphics g) {
    myIcon.paintIcon(this, g, 0, 0);
  }
}
