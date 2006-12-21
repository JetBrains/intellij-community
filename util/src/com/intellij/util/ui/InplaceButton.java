package com.intellij.util.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public abstract class InplaceButton extends JComponent {

  private boolean myActive = true;

  private Icon myRegualar;
  private Icon myHovered;
  private BaseButtonBehavior myBehavior;

  public InplaceButton(final Icon icon) {
    this(icon, icon);
  }

  public InplaceButton(final Icon regular, final Icon hovered) {
    myBehavior = new BaseButtonBehavior(this) {
      protected void execute() {
        InplaceButton.this.execute();
      }
    };

    int width = Math.max(regular.getIconWidth(), regular.getIconWidth());
    int height = Math.max(hovered.getIconHeight(), hovered.getIconHeight());
    
    setPreferredSize(new Dimension(width, height));

    myRegualar = new CenteredIcon(regular, width, height);
    myHovered = new CenteredIcon(hovered, width, height);

    setBorder(new EmptyBorder(2, 2, 2, 2));
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  protected abstract void execute();


  public void setActive(final boolean active) {
    myActive = active;
  }

  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (!myActive) return;

    if (myBehavior.isHovered() || myBehavior.isPressedByMouse()) {
      myHovered.paintIcon(this, g, 0, 0);
    } else {
      myRegualar.paintIcon(this, g, 0, 0);
    }
  }
}
