package com.intellij.uiDesigner.quickFixes;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.uiDesigner.UIDesignerBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.MessageFormat;

/**
 * [vova] This class should be inner but due to bugs in "beta" generics compiler
 * I need to use "static" modifier.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class LightBulbComponentImpl extends JComponent{
  private final QuickFixManager myManager;
  private final BufferedImage myBackgroundImage;

  public LightBulbComponentImpl(final QuickFixManager manager, final BufferedImage backgroundImage) {
    if(manager == null){
      throw new IllegalArgumentException();
    }
    if (backgroundImage == null) {
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("backgroundImage cannot be null");
    }
    myManager = manager;
    myBackgroundImage = backgroundImage;

    setPreferredSize(new Dimension(backgroundImage.getWidth(), backgroundImage.getHeight()));
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
    // 1. Paint background
    g.drawImage(myBackgroundImage, 0, 0, myBackgroundImage.getWidth(), myBackgroundImage.getHeight(), this);

    // 2. Paint rollover border
    // TODO[vova] implement

    // 3. Paint dropdown arrow
    // TODO[vova] implement
  }
}
