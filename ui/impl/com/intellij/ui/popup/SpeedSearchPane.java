/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.awt.*;

public class SpeedSearchPane extends JDialog {

  private static final Color SPEEDSEARCH_BACKGROUND = new Color(244, 249, 181);
  private static final Color SPEEDSEARCH_FOREGROUND = Color.black;

  private BasePopup myPopup;
  private JLabel myLabel = new JLabel();

  private JPanel myPanel = new JPanel();

  private Dimension myLastLabelSize = new Dimension();
  private static final Icon ICON_PROMPT = IconLoader.getIcon("/icons/ide/speedSearchPrompt.png");

  public SpeedSearchPane(BasePopup popup) throws HeadlessException {
    myPopup = popup;
    setUndecorated(true);
    setFocusableWindowState(false);

    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(myPanel, BorderLayout.CENTER);

    myPanel.setLayout(new BorderLayout());
    myPanel.setOpaque(true);
    myPanel.add(myLabel, BorderLayout.CENTER);

    myPanel.setBackground(SPEEDSEARCH_BACKGROUND);
    myLabel.setIcon(ICON_PROMPT);

    myPanel.setBorder(BorderFactory.createLineBorder(SPEEDSEARCH_FOREGROUND));
    myLabel.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
  }

  public void update() {
    if (!isShowing()) {
      if (myPopup.getSpeedSearch().isHoldingFilter()) {
        setVisible(true);

        final JLabel title = myPopup.getTitle();
        final Point titleScreenPoint = title.getLocationOnScreen();
        setLocation(new Point(titleScreenPoint.x + title.getSize().width / 4, titleScreenPoint.y - title.getSize().height / 2));
        updateTextAndBounds();
      }
    }
    else {
      if (!myPopup.getSpeedSearch().isHoldingFilter()) {
        setVisible(false);
      }
      else {
        updateTextAndBounds();
      }
    }
  }

  private void updateTextAndBounds() {
    myLabel.setText(myPopup.getSpeedSearch().getFilter());

    if (myLabel.getPreferredSize().width > myLastLabelSize.width) {
      pack();
      myLastLabelSize = myLabel.getPreferredSize();
    }

    myPanel.repaint();
  }

}
