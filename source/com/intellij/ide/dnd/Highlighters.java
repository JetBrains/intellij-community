/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.dnd;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;

import javax.accessibility.Accessible;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Highlighters implements DnDEvent.DropTargetHighlightingType {
  private static List<Accessible> ourHightlighters = new ArrayList<Accessible>();

  private static List<DropTargetHighlighter> ourCurrentHighlighters = new ArrayList<DropTargetHighlighter>();

  static {
    ourHightlighters.add(new RectangleHighlighter());
    ourHightlighters.add(new FilledRectangleHighlighter());
    ourHightlighters.add(new HorizontalLinesHighlighter());
    ourHightlighters.add(new TextHighlighter());
    ourHightlighters.add(new ErrorTextHighlighter());
    ourHightlighters.add(new VerticalLinesHighlighter());
  }

  static void show(int aType, JLayeredPane aPane, Rectangle aRectangle, DnDEvent aEvent) {
    List<DropTargetHighlighter> toShow = new ArrayList<DropTargetHighlighter>();
    for (Accessible ourHightlighter : ourHightlighters) {
      DropTargetHighlighter each = (DropTargetHighlighter)ourHightlighter;
      if ((each.getMask() & aType) != 0) {
        toShow.add(each);
      }
    }

    for (int i = 0; i < toShow.size(); i++) {
      DropTargetHighlighter each = toShow.get(i);
      each.show(aPane, aRectangle, aEvent);
    }
    ourCurrentHighlighters.addAll(toShow);
  }

  static void hideAllBut(int aType) {
    for (int i = 0; i < ourCurrentHighlighters.size(); i++) {
      final DropTargetHighlighter each = ourCurrentHighlighters.get(i);
      if ((each.getMask() & aType) == 0) {
        each.vanish();
        ourCurrentHighlighters.remove(each);
      }
    }
  }

  static void hide() {
    for (int i = 0; i < ourCurrentHighlighters.size(); i++) {
      (ourCurrentHighlighters.get(i)).vanish();
    }
    ourCurrentHighlighters.clear();
  }

  static void hide(int aType) {
    for (int i = 0; i < ourCurrentHighlighters.size(); i++) {
      final DropTargetHighlighter each = ourCurrentHighlighters.get(i);
      if ((each.getMask() & aType) != 0) {
        each.vanish();
        ourCurrentHighlighters.remove(each);
      }
    }
  }

  static boolean isVisibleExcept(int type) {
    int resultType = type;
    for (int i = 0; i < ourCurrentHighlighters.size(); i++) {
      final DropTargetHighlighter each = ourCurrentHighlighters.get(i);
      resultType = resultType | each.getMask();
    }

    return type != resultType;
  }

  static boolean isVisible() {
    return ourCurrentHighlighters.size() > 0;
  }

  private static abstract class AbstractComponentHighlighter extends JPanel implements DropTargetHighlighter {

    protected AbstractComponentHighlighter() {
      setOpaque(false);
      setLayout(new BorderLayout());
    }

    public final void show(JLayeredPane aPane, Rectangle aRectangle, DnDEvent aEvent) {
      if (getParent() != aPane) {
        vanish();
        aPane.add(this, getLayer());
      }
      _show(aPane, aRectangle, aEvent);
    }

    protected Integer getLayer() {
      return JLayeredPane.MODAL_LAYER;
    }

    public void vanish() {
      final Container parent = getParent();
      Rectangle bounds = getBounds();
      if (parent != null) {
        parent.remove(this);
        parent.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
      }
    }

    protected abstract void _show(JLayeredPane aPane, Rectangle aRectangle, DnDEvent aEvent);
  }

  private abstract static class BaseTextHighlighter extends JWindow implements DropTargetHighlighter {
    protected JLabel myLabel;

    public BaseTextHighlighter() {
      myLabel = new JLabel("", JLabel.CENTER) {
        protected void paintComponent(Graphics g) {
          BaseTextHighlighter.this.paintComponent(g);
          super.paintComponent(g);
        }
      };
      myLabel.setFont(myLabel.getFont().deriveFont(Font.BOLD));
      myLabel.setForeground(UIManager.getColor("ToolTip.foreground"));

      setFocusable(false);

      getContentPane().setLayout(new BorderLayout());
      getContentPane().add(myLabel, BorderLayout.CENTER);
    }

    public void show(JLayeredPane aPane, Rectangle aRectangle, DnDEvent aEvent) {
      myLabel.setText(aEvent.getExpectedDropResult());
      final Dimension prefSize = getPreferredSize();
      prefSize.width += 10;
      prefSize.height += 4;

      int centerX = aRectangle.x + aRectangle.width / 2;
      final Rectangle newBounds = new Rectangle(centerX - prefSize.width / 2, aRectangle.y - prefSize.height - 5, prefSize.width, prefSize.height);
      newBounds.y = newBounds.y < 0 ? 0 : newBounds.y;

      Point location = newBounds.getLocation();
      SwingUtilities.convertPointToScreen(location, aPane);
      newBounds.setLocation(location);

      setBounds(newBounds);
      show();
      if (SystemInfo.isUnix) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            toFront();
          }
        });
      }
    }

    public void vanish() {
      hide();
    }

    protected void paintComponent(Graphics g) {
      Graphics2D g2d = (Graphics2D) g;
      Object old = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      g.setColor(UIManager.getColor("ToolTip.background"));
      g.fillRoundRect(0, 0, getSize().width - 1, getSize().height - 1, 6, 6);

      g.setColor(UIManager.getColor("ToolTip.foreground"));
      g.drawRoundRect(0, 0, getSize().width - 1, getSize().height - 1, 6, 6);
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, old);
    }

    protected Integer getLayer() {
      return JLayeredPane.POPUP_LAYER;
    }

  }

  private static class TextHighlighter extends BaseTextHighlighter {
    public int getMask() {
      return TEXT;
    }
  }

  private static class ErrorTextHighlighter extends BaseTextHighlighter {
    public ErrorTextHighlighter() {
      super();
      myLabel.setIcon(IconLoader.getIcon("/ide/dnd/error.png"));
    }

    public int getMask() {
      return ERROR_TEXT;
    }
  }

  private static class FilledRectangleHighlighter extends AbstractComponentHighlighter {
    public FilledRectangleHighlighter() {
      super();
      setOpaque(true);
      setBorder(BorderFactory.createLineBorder(Color.red));
      setBackground(Color.red);
    }

    protected void _show(JLayeredPane aPane, Rectangle aRectangle, DnDEvent aEvent) {
      setBounds(aRectangle);
    }

    public int getMask() {
      return FILLED_RECTANGLE;
    }
  }

  private static class RectangleHighlighter extends AbstractComponentHighlighter {
    public RectangleHighlighter() {
      super();
      setOpaque(false);
      setBorder(BorderFactory.createLineBorder(Color.red));
    }

    protected void _show(JLayeredPane aPane, Rectangle aRectangle, DnDEvent aEvent) {
      setBounds(aRectangle);
    }

    public int getMask() {
      return RECTANGLE;
    }
  }

  private static class HorizontalLinesHighlighter extends AbstractComponentHighlighter {
    private Icon myLeft = IconLoader.getIcon("/ide/dnd/left.png");
    private Icon myRight = IconLoader.getIcon("/ide/dnd/right.png");

    protected void _show(JLayeredPane aPane, Rectangle aRectangle, DnDEvent aEvent) {
      final Rectangle rectangle = new Rectangle(aRectangle.x - myLeft.getIconWidth(), aRectangle.y - myLeft.getIconHeight(), aRectangle.width + myLeft.getIconWidth() + myRight.getIconWidth(), aRectangle.height + myLeft.getIconHeight());
      setBounds(rectangle);
    }

    protected void paintComponent(Graphics g) {
      myLeft.paintIcon(this, g, 0, (getHeight() / 2));
      myRight.paintIcon(this, g, getWidth() - myRight.getIconWidth(), (getHeight() / 2));
    }

    public int getMask() {
      return H_ARROWS;
    }
  }

  private static class VerticalLinesHighlighter extends AbstractComponentHighlighter {
    private Icon myTop = IconLoader.getIcon("/ide/dnd/top.png");
    private Icon myBottom = IconLoader.getIcon("/ide/dnd/bottom.png");

    protected void _show(JLayeredPane aPane, Rectangle aRectangle, DnDEvent aEvent) {
      final Rectangle rectangle = new Rectangle(aRectangle.x, aRectangle.y - myTop.getIconHeight(), aRectangle.width, aRectangle.height + myTop.getIconHeight() + myBottom.getIconHeight());
      setBounds(rectangle);
    }

    protected void paintComponent(Graphics g) {
      myTop.paintIcon(this, g, (getWidth() - myTop.getIconWidth()) / 2, 0);
      myBottom.paintIcon(this, g, (getWidth() - myBottom.getIconWidth()) / 2, getHeight() - myBottom.getIconHeight());
    }

    public int getMask() {
      return V_ARROWS;
    }
  }
}
