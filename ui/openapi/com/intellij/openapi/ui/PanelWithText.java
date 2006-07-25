/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 16-Jul-2006
 * Time: 17:27:18
 */
package com.intellij.openapi.ui;

import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

public class PanelWithText extends JPanel {
  private JLabel myLabel = new JLabel();

  public PanelWithText() {
    this("");
  }

  public PanelWithText(String text) {
    super(new GridBagLayout());
    setBorder(BorderFactory.createEtchedBorder());
    myLabel.setText(wrapText(text));
    add(myLabel, new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(8,8,8,8), 0, 0));
  }

  private static String wrapText(final String text) {
    @NonNls String opentTag = "<html>";
    @NonNls String closeTag = "</html>";
    return opentTag + text + closeTag;
  }

  public void setText(String text){
    myLabel.setText(wrapText(text));
  }
}