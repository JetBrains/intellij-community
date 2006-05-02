package com.intellij.ui.plaf.beg;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalTabbedPaneUI;
import java.awt.*;

public class BegTabbedPaneUI extends MetalTabbedPaneUI {
  private static Color LIGHT = new Color(247, 243, 239);
  private static Color DARK = new Color(189, 187, 182);

  private boolean myNoIconSpace = false;
  private boolean myPaintContentBorder = true;

  public void installUI(JComponent c) {
    super.installUI(c);
    Object clientProperty = UIUtil.getTabbedPanePaintContentBorder(c);
    if (clientProperty instanceof Boolean) {
      Boolean aBoolean = (Boolean)clientProperty;
      myPaintContentBorder = aBoolean.booleanValue();
    }
  }

  protected Insets getContentBorderInsets(int tabPlacement) {
    if (tabPlacement == TOP && !myPaintContentBorder) {
      return new Insets(1, 0, 0, 0);
    }
    if (tabPlacement == BOTTOM && !myPaintContentBorder) {
      return new Insets(0, 0, 1, 0);
    }
    if (tabPlacement == LEFT && !myPaintContentBorder) {
      return new Insets(0, 1, 0, 0);
    }
    if (tabPlacement == RIGHT && !myPaintContentBorder) {
      return new Insets(0, 0, 0, 1);
    }
    return new Insets(1, 1, 1, 1);
  }

  protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
    g.setColor(darkShadow);
    switch (tabPlacement) {
      case TOP:
        {
          if (isSelected) {
            // left
            UIUtil.drawLine(g, x, y + 1, x, y + h - 1);
            // top
            UIUtil.drawLine(g, x + 1, y, x + w - 3, y);
            // right
            UIUtil.drawLine(g, x + w - 2, y + 1, x + w - 2, y + h - 1);
          }
          else {
            // left
            UIUtil.drawLine(g, x, y + 1, x, y + h - 1);
            // top
            UIUtil.drawLine(g, x + 1, y, x + w - 3, y);
            // right
            UIUtil.drawLine(g, x + w - 2, y + 1, x + w - 2, y + h - 1);
          }
          break;
        }
      case LEFT:
        {
          // top
          UIUtil.drawLine(g, x + 1, y + 1, x + w - 1, y + 1);
          // left
          UIUtil.drawLine(g, x, y + 2, x, y + h - 2);
          //bottom
          UIUtil.drawLine(g, x + 1, y + h - 1, x + w - 1, y + h - 1);
          break;
        }
      case BOTTOM:
        {
          if (isSelected) {
            // left
            UIUtil.drawLine(g, x, y, x, y + h - 2);
            // bottom
            UIUtil.drawLine(g, x + 1, y + h - 1, x + w - 2, y + h - 1);
            // right
            UIUtil.drawLine(g, x + w - 1, y, x + w - 1, y + h - 2);
          }
          else {
            // left
            UIUtil.drawLine(g, x, y, x, y + h - 1);
            // bottom
            UIUtil.drawLine(g, x + 1, y + h - 1, x + w - 3, y + h - 1);
            // right
            UIUtil.drawLine(g, x + w - 2, y, x + w - 2, y + h - 1);
          }
          break;
        }
      case RIGHT:
        {
          // top
          UIUtil.drawLine(g, x, y + 1, x + w - 2, y + 1);
          // right
          UIUtil.drawLine(g, x + w - 1, y + 2, x + w - 1, y + h - 2);
          //bottom
          UIUtil.drawLine(g, x, y + h - 1, x + w - 2, y + h - 1);
          break;
        }
      default:
        {
          throw new IllegalArgumentException("unknown tabPlacement: " + tabPlacement);
        }
    }
  }

  protected void paintText(Graphics g, int tabPlacement,
                           Font font, FontMetrics metrics, int tabIndex,
                           String title, Rectangle textRect,
                           boolean isSelected) {
    if (isSelected) {
      font = font.isBold()? font : font.deriveFont(Font.BOLD);
      metrics = metrics.getFont().isBold()? metrics : g.getFontMetrics(font);
    }
    else {
      font = font.isPlain()? font : font.deriveFont(Font.PLAIN);
      metrics = metrics.getFont().isPlain()? metrics : g.getFontMetrics(font);
    }

    g.setFont(font);
    int y = textRect.y + metrics.getAscent();
    if (tabPane.isEnabled() && tabPane.isEnabledAt(tabIndex)) {
      g.setColor(tabPane.getForegroundAt(tabIndex));

      int x = textRect.x - (myNoIconSpace ? 5 : 0);
      g.drawString(title, x, y);

      //FontRenderContext frc = ((Graphics2D)g).getFontRenderContext();
      //double titleWidth = font.getStringBounds(title, frc).getWidth();
      //String text = "";
      //while (font.getStringBounds(text,frc).getWidth() < titleWidth) {
      //  text = text + " ";
      //}
      //AttributedString attributedString = new AttributedString(text);
      //attributedString.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_LOW_GRAY);
      //g.setColor(Color.red);
      //g.drawString(attributedString.getIterator(), x, y);
    }
    else {
      // tab disabled
      g.setColor(tabPane.getBackgroundAt(tabIndex).brighter());
      g.drawString(title, textRect.x, y);
      g.setColor(tabPane.getBackgroundAt(tabIndex).darker());
      g.drawString(title, textRect.x - (myNoIconSpace ? 6 : 1), y - 1);
    }
  }

  protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
    if (isSelected) {
      g.setColor(LIGHT);
    }
    else {
      g.setColor(DARK);
    }
    switch (tabPlacement) {
      case LEFT:
        g.fillRect(x + 1, y + 2, w - 2, h - 3);
        break;
      case RIGHT:
        g.fillRect(x, y + 2, w - 1, h - 3);
        break;
      case BOTTOM:
        g.fillRect(x + 1, y, w - 3, h - 1);
        break;
      case TOP:
      default:
        g.fillRect(x + 1, y + 1, w - 2, h);
    }
  }

  protected void paintContentBorderTopEdge(Graphics g, int tabPlacement, int selectedIndex, int x, int y, int w, int h) {
    if (tabPlacement == TOP || myPaintContentBorder) {
      boolean leftToRight = isLeftToRight(tabPane);
      int right = x + w - 1;
      Rectangle selRect = selectedIndex < 0 ? null :
                          getTabBounds(selectedIndex, calcRect);
      g.setColor(darkShadow);

      // Draw unbroken line if tabs are not on TOP, OR
      // selected tab is not in run adjacent to content, OR
      // selected tab is not visible (SCROLL_TAB_LAYOUT)
      //
      if (tabPlacement != TOP || selectedIndex < 0 ||
          (selRect.y + selRect.height + 1 < y) ||
          (selRect.x < x || selRect.x > x + w)) {
        UIUtil.drawLine(g, x, y, x + w - 1, y);
      }
      else {
        // Break line to show visual connection to selected tab
        boolean lastInRun = isLastInRun(selectedIndex);

        UIUtil.drawLine(g, x, y, selRect.x, y);

        if (selRect.x + selRect.width < right - 1) {
          if (leftToRight && !lastInRun) {
            UIUtil.drawLine(g, selRect.x + selRect.width - 2, y, right, y);
          }
          else {
            UIUtil.drawLine(g, selRect.x + selRect.width - 2, y, right, y);
          }
        }
        else {
          UIUtil.drawLine(g, x + w - 2, y, x + w - 2, y);
        }
      }
    }
  }

  protected void paintContentBorderBottomEdge(Graphics g, int tabPlacement, int selectedIndex, int x, int y, int w, int h) {
    if (tabPlacement == BOTTOM || myPaintContentBorder) {
      boolean leftToRight = isLeftToRight(tabPane);
      int bottom = y + h - 1;
      int right = x + w - 1;
      Rectangle selRect = selectedIndex < 0 ? null :
                          getTabBounds(selectedIndex, calcRect);
      g.setColor(darkShadow);

      // Draw unbroken line if tabs are not on BOTTOM, OR
      // selected tab is not in run adjacent to content, OR
      // selected tab is not visible (SCROLL_TAB_LAYOUT)
      //
      if (tabPlacement != BOTTOM || selectedIndex < 0 ||
          (selRect.y - 1 > h) ||
          (selRect.x < x || selRect.x > x + w)) {
        UIUtil.drawLine(g, x, y + h - 1, x + w - 1, y + h - 1);
      }
      else {
        // Break line to show visual connection to selected tab
        boolean lastInRun = isLastInRun(selectedIndex);

        if (leftToRight || lastInRun) {
          UIUtil.drawLine(g, x, bottom, selRect.x, bottom);
        }
        else {
          UIUtil.drawLine(g, x, bottom, selRect.x - 1, bottom);
        }

        if (selRect.x + selRect.width < x + w - 2) {
          if (leftToRight && !lastInRun) {
            UIUtil.drawLine(g, selRect.x + selRect.width, bottom, right, bottom);
          }
          else {
            UIUtil.drawLine(g, selRect.x + selRect.width - 1, bottom, right, bottom);
          }
        }
      }
    }
  }

  protected void paintContentBorderLeftEdge(Graphics g, int tabPlacement, int selectedIndex, int x, int y, int w, int h) {
    if (tabPlacement == LEFT || myPaintContentBorder) {
      Rectangle selRect = selectedIndex < 0 ? null :
                          getTabBounds(selectedIndex, calcRect);
      g.setColor(darkShadow);

      // Draw unbroken line if tabs are not on LEFT, OR
      // selected tab is not in run adjacent to content, OR
      // selected tab is not visible (SCROLL_TAB_LAYOUT)
      //
      if (tabPlacement != LEFT || selectedIndex < 0 ||
          (selRect.x + selRect.width + 1 < x) ||
          (selRect.y < y || selRect.y > y + h)) {
        UIUtil.drawLine(g, x, y, x, y + h - 2);
      }
      else {
        // Break line to show visual connection to selected tab
        UIUtil.drawLine(g, x, y, x, selRect.y + 1);
        if (selRect.y + selRect.height < y + h - 2) {
          UIUtil.drawLine(g, x, selRect.y + selRect.height + 1, x, y + h + 2);
        }
      }
    }
  }

  protected void paintContentBorderRightEdge(Graphics g, int tabPlacement, int selectedIndex, int x, int y, int w, int h) {
    if (tabPlacement == RIGHT || myPaintContentBorder) {
      Rectangle selRect = selectedIndex < 0 ? null :
                          getTabBounds(selectedIndex, calcRect);
      g.setColor(darkShadow);

      // Draw unbroken line if tabs are not on RIGHT, OR
      // selected tab is not in run adjacent to content, OR
      // selected tab is not visible (SCROLL_TAB_LAYOUT)
      //
      if (tabPlacement != RIGHT || selectedIndex < 0 ||
          (selRect.x - 1 > w) ||
          (selRect.y < y || selRect.y > y + h)) {
        UIUtil.drawLine(g, x + w - 1, y, x + w - 1, y + h - 1);
      }
      else {
        // Break line to show visual connection to selected tab
        UIUtil.drawLine(g, x + w - 1, y, x + w - 1, selRect.y);

        if (selRect.y + selRect.height < y + h - 2) {
          UIUtil.drawLine(g, x + w - 1, selRect.y + selRect.height, x + w - 1, y + h - 2);
        }
      }
    }
  }

  private boolean isLastInRun(int tabIndex) {
    int run = getRunForTab(tabPane.getTabCount(), tabIndex);
    int lastIndex = lastTabInRun(tabPane.getTabCount(), run);
    return tabIndex == lastIndex;
  }

  static boolean isLeftToRight(Component c) {
    return c.getComponentOrientation().isLeftToRight();
  }

  protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
    return (int)(super.calculateTabHeight(tabPlacement, tabIndex, fontHeight) * 1.0);
  }

  protected int calculateMaxTabHeight(int tabPlacement) {
    FontMetrics metrics = getFontMetrics();
    int tabCount = tabPane.getTabCount();
    int result = 0;
    int fontHeight = metrics.getHeight();
    for (int i = 0; i < tabCount; i++) {
      result = Math.max(calculateTabHeight(tabPlacement, i, fontHeight), result);
    }
    return result;
  }

  /**
   * invoked by reflection
   */
  public static ComponentUI createUI(JComponent c) {
    return new BegTabbedPaneUI();
  }

  /**
   * IdeaTabbedPaneUI uses bold font for selected tab. Bold width of some fonts is
   * less then width of plain font. To handle correctly this "anomaly" we have to
   * determine maximum of these two widths.
   */
  protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
    final Font font = metrics.getFont();
    final FontMetrics plainMetrics = font.isPlain()? metrics : tabPane.getFontMetrics(font.deriveFont(Font.PLAIN));
    final int widthPlain = super.calculateTabWidth(tabPlacement, tabIndex, plainMetrics);

    final FontMetrics boldMetrics = font.isBold()? metrics : tabPane.getFontMetrics(font.deriveFont(Font.BOLD));
    final int widthBold = super.calculateTabWidth(tabPlacement, tabIndex, boldMetrics);

    final int width = Math.max(widthPlain, widthBold);

    myLayoutMetrics = (width == widthPlain)? plainMetrics : boldMetrics;

    return width;
  }

  private FontMetrics myLayoutMetrics = null;

  protected void layoutLabel(int tabPlacement, FontMetrics metrics, int tabIndex, String title, Icon icon, Rectangle tabRect,
                             Rectangle iconRect, Rectangle textRect, boolean isSelected) {

    final FontMetrics _metrics = (myLayoutMetrics != null)? myLayoutMetrics : metrics;
    super.layoutLabel(tabPlacement, _metrics, tabIndex, title, icon, tabRect, iconRect, textRect, isSelected);
  }

  public void setNoIconSpace(boolean noIconSpace) {
    myNoIconSpace = noIconSpace;
  }

  public void setPaintContentBorder(boolean paintContentBorder) {
    myPaintContentBorder = paintContentBorder;
  }

  protected int calculateTabAreaHeight(int tabPlacement, int horizRunCount, int maxTabHeight) {
    for (int i = 0; i < tabPane.getTabCount(); i++) {
      Component component = tabPane.getComponentAt(i);
      if (component != null) {
        return super.calculateTabAreaHeight(tabPlacement, horizRunCount, maxTabHeight);
      }
    }
    return maxTabHeight + tabRunOverlay;
  }

}
