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
  private Icon myRegular;
  private Icon myInactive;

  public TitlePanel() {
    this(null, null);
  }

  public TitlePanel(Icon regular, Icon inactive) {
    myRegular = regular;
    myInactive = inactive;

    myLabel = new JLabel();
    myLabel.setOpaque(false);
    myLabel.setForeground(Color.black);
    myLabel.setHorizontalAlignment(SwingConstants.CENTER);
    myLabel.setVerticalAlignment(SwingConstants.CENTER);
    myLabel.setBorder(new EmptyBorder(1, 2, 2, 2));

    add(myLabel, BorderLayout.CENTER);

    setActive(false);
  }

  public void setActive(final boolean active) {
    super.setActive(active);
    myLabel.setIcon(active ? myRegular : myInactive);
  }

  public void setText(String titleText) {
    myLabel.setText(titleText);
  }

}
