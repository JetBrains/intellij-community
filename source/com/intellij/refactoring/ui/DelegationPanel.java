package com.intellij.refactoring.ui;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author dsl
 */
public class DelegationPanel extends JPanel {
  private final JRadioButton myRbModifyCalls;
  private final JRadioButton myRbGenerateDelegate;

  public DelegationPanel() {
    final BoxLayout boxLayout = new BoxLayout(this, BoxLayout.X_AXIS);
    setLayout(boxLayout);
    add(new JLabel("Method calls:"));
    myRbModifyCalls = new JRadioButton("Modify");
    myRbModifyCalls.setMnemonic('M');
    add(myRbModifyCalls);
    myRbGenerateDelegate = new JRadioButton("Delegate via overloading method");
    myRbGenerateDelegate.setMnemonic('l');
    add(myRbGenerateDelegate);
    myRbModifyCalls.setSelected(true);
    final ButtonGroup bg = new ButtonGroup();
    bg.add(myRbModifyCalls);
    bg.add(myRbGenerateDelegate);
    add(Box.createHorizontalGlue());
    myRbModifyCalls.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        stateModified();
      }
    });
    myRbGenerateDelegate.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        stateModified();
      }
    });
  }

  protected void stateModified() {

  }

  public boolean isModifyCalls() {
    return myRbModifyCalls.isSelected();
  }

  public boolean isGenerateDelegate() {
    return myRbGenerateDelegate.isSelected();
  }
}
