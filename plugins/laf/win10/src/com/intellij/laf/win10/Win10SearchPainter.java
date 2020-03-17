package com.intellij.laf.win10;

import com.intellij.ide.ui.laf.PluggableLafInfo;
import com.intellij.ide.ui.laf.SearchTextAreaPainter;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

class Win10SearchPainter implements SearchTextAreaPainter, Border {
  private final PluggableLafInfo.SearchAreaContext myContext;

  Win10SearchPainter(PluggableLafInfo.SearchAreaContext context) {
    myContext = context;
    MouseListener ml = new MouseAdapter() {
      @Override public void mouseEntered(MouseEvent e) {
        setHover(true);
      }

      @Override public void mouseExited(MouseEvent e) {
        setHover(false);
      }

      private void setHover(Boolean hover) {
        JComponent c = myContext.getSearchComponent();
        c.putClientProperty(WinIntelliJTextFieldUI.HOVER_PROPERTY, hover);
        c.repaint();
      }
    };

    myContext.getTextComponent().addMouseListener(ml);
    myContext.getSearchComponent().addMouseListener(ml);
  }

  @Override
  @NotNull
  public String getLayoutConstraints() {
    Insets i = JBUI.insets(1, 1, 2, 1);
    return "flowx, ins " + i.top + " " + i.left + " " + i.bottom + " " + i.right + ", gapx " + JBUIScale.scale(3);
  }

  @NotNull
  @Override
  public String getHistoryButtonConstraints() {
    return "ay baseline, gaptop " + JBUIScale.scale(1);
  }

  @NotNull
  @Override
  public String getIconsPanelConstraints() {
    return "ay baseline";
  }

  @NotNull
  @Override
  public Border getIconsPanelBorder(int rows) {
    return JBUI.Borders.empty();
  }

  @Override
  @NotNull
  public Border getBorder() {
    return this;
  }

  @Override
  public void paint(@NotNull Graphics2D g) {
    JComponent c = myContext.getSearchComponent();
    Rectangle r = new Rectangle(c.getSize());
    JBInsets.removeFrom(r, c.getInsets());

    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setColor(myContext.getTextComponent().getBackground());
      g2.fill(r);
    } finally {
      g2.dispose();
    }
  }

  @Override public Insets getBorderInsets(Component c) {
    return JBInsets.create(1, 0).asUIResource();
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Graphics2D g2 = (Graphics2D)g.create();
    JComponent jc = (JComponent)c;
    try {
      Insets i = jc.getInsets();
      g2.translate(x + i.left, y + i.top);
      width -= i.left + i.right;
      height -= i.top + i.bottom;

      if (myContext.getTextComponent().hasFocus()) {
        g2.setColor(UIManager.getColor("TextField.focusedBorderColor"));
      } else if (jc.isEnabled() && jc.getClientProperty(WinIntelliJTextFieldUI.HOVER_PROPERTY) == Boolean.TRUE) {
        g2.setColor(UIManager.getColor("TextField.hoverBorderColor"));
      } else {
        g2.setColor(UIManager.getColor("TextField.borderColor"));
      }

      int bw = JBUIScale.scale(1);
      Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      border.append(new Rectangle2D.Float(0, 0, width, height), false);
      border.append(new Rectangle2D.Float(bw, bw, width - bw*2, height - bw*2), false);

      g2.fill(border);
    } finally {
      g2.dispose();
    }
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}