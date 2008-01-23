package com.intellij.refactoring.util;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * @author yole
*/
public class RadioUpDownListener extends KeyAdapter {
  private JRadioButton myRadioButton1;
  private JRadioButton myRadioButton2;

  public RadioUpDownListener(final JRadioButton radioButton1, final JRadioButton radioButton2) {
    myRadioButton1 = radioButton1;
    myRadioButton2 = radioButton2;
    myRadioButton1.addKeyListener(this);
    myRadioButton2.addKeyListener(this);
  }

  public void keyPressed(final KeyEvent e) {
    if (e.getKeyCode() == KeyEvent.VK_UP || e.getKeyCode() == KeyEvent.VK_DOWN) {
      if (myRadioButton1.isSelected()) {
        myRadioButton2.requestFocus();
        myRadioButton2.doClick();
      }
      else {
        myRadioButton1.requestFocus();
        myRadioButton1.doClick();
      }
    }
  }
}
