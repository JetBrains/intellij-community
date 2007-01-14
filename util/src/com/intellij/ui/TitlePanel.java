/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * @author max
 */
public class TitlePanel extends CaptionPanel {

  private JLabel myLabel;

  public TitlePanel() {
    myLabel = new JLabel();
    myLabel.setOpaque(false);
    myLabel.setForeground(Color.black);
    myLabel.setHorizontalAlignment(SwingConstants.CENTER);
    myLabel.setVerticalAlignment(SwingConstants.CENTER);
    myLabel.setBorder(new EmptyBorder(1, 2, 2, 2));

    add(myLabel, BorderLayout.CENTER);
  }

  public void setText(String titleText) {
    myLabel.setText(titleText);
  }

}
