package com.intellij.jsf.ui.forms;

import javax.swing.*;
import java.awt.*;

/**
 * User: Sergey.Vasiliev
 */
public class CaptionComponent extends JPanel {
  private JPanel myRootPanel;
  private JLabel myCaptionLabel;

  private boolean myBordered = true;

  public CaptionComponent() {
    updateBorder();
    setLayout(new BorderLayout());
    add(myRootPanel, BorderLayout.CENTER);
  }

  private void updateBorder() {
    if (myBordered) {
      myRootPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.BLACK));
    }
    else {
      myRootPanel.setBorder(BorderFactory.createEmptyBorder());
    }
  }

  public void setText(final String text) {
    myCaptionLabel.setText(text);
  }

  public String getText() {
    return myCaptionLabel.getText();
  }

  public boolean isBordered() {
    return myBordered;
  }

  public void setBordered(final boolean bordered) {
    myBordered = bordered;
    updateBorder();
  }
}
