package com.intellij.ui;

import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.CenteredIcon;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public final class InplaceButton extends JComponent implements ActiveComponent {

  private boolean myPainting = true;
  private boolean myActive = true;

  private BaseButtonBehavior myBehavior;

  private CenteredIcon myRegular;
  private CenteredIcon myHovered;
  private CenteredIcon myInactive;

  public InplaceButton(IconButton source, final ActionListener listener) {
    myBehavior = new BaseButtonBehavior(this) {
      protected void execute() {
        listener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "execute"));
      }
    };

    int width = Math.max(source.getRegular().getIconWidth(), source.getInactive().getIconWidth());
    width = Math.max(width, source.getHovered().getIconWidth());

    int height = Math.max(source.getRegular().getIconHeight(), source.getInactive().getIconHeight());
    height = Math.max(height, source.getHovered().getIconHeight());
    
    setPreferredSize(new Dimension(width, height));

    myRegular = new CenteredIcon(source.getRegular(), width, height);
    myHovered = new CenteredIcon(source.getHovered(), width, height);
    myInactive = new CenteredIcon(source.getInactive(), width, height);

    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    setToolTipText(source.getTooltip());
  }

  public void setPainting(final boolean active) {
    myPainting = active;
    repaint();
  }

  public void setActive(final boolean active) {
    myActive = active;
    repaint();
  }


  public JComponent getComponent() {
    return this;
  }

  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (!myPainting) return;

    if (myBehavior.isHovered()) {
      if (myBehavior.isPressedByMouse()) {
        myHovered.paintIcon(this, g, 1, 1);
      } else {
        myHovered.paintIcon(this, g, 0, 0);
      }
    } else {
      if (myActive) {
        myRegular.paintIcon(this, g, 0, 0);
      } else {
        myInactive.paintIcon(this, g, 0, 0);
      }
    }
  }


}
