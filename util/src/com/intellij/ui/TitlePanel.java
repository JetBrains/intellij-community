/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

  public void setFontBold(boolean bold) {
    myLabel.setFont(myLabel.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN));
  }

  public void setActive(final boolean active) {
    super.setActive(active);
    myLabel.setIcon(active ? myRegular : myInactive);
    myLabel.setForeground(active ? UIManager.getColor("Label.foreground") : Color.gray);
  }

  public void setText(String titleText) {
    myLabel.setText(titleText);
  }


  public Dimension getPreferredSize() {
    final String text = myLabel.getText();
    return text != null && text.trim().length() > 0 ? super.getPreferredSize() : new Dimension(0, 0);
  }

  public static void main(String[] args) {
    final JFrame jFrame = new JFrame();

    jFrame.getContentPane().setLayout(new BorderLayout());
    jFrame.getContentPane().setBackground(Color.white);

    final JPanel jPanel = new JPanel(new BorderLayout());
    jPanel.setBackground(Color.white);
    jPanel.setOpaque(true);

    jPanel.setBorder(PopupBorder.Factory.create(true));

    jFrame.getContentPane().add(jPanel);

    jFrame.setBounds(100, 100, 200, 200);


    jFrame.setVisible(true);
  }
}


