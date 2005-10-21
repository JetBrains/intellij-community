package com.intellij.ui;

import com.intellij.util.text.StringTokenizer;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
public class LabeledIcon implements Icon {
  private final Icon myIcon;
  private String myMnemonic;
  private final String[] myStrings;
  private int myIconTextGap = 0;

  /**
   * @param icon     not <code>null</code> icon.
   * @param text     to be painted under the <code>icon<code>. This parameter can
   *                 be <code>null</code> if text isn't specified. In that case <code>LabeledIcon</code>
   * @param mnemonic
   */
  public LabeledIcon(Icon icon, String text, String mnemonic) {
    myIcon = icon;
    myMnemonic = mnemonic;
    if (text != null) {
      StringTokenizer tokenizer = new StringTokenizer(text, "\n");
      myStrings = new String[tokenizer.countTokens()];
      for (int i = 0; tokenizer.hasMoreTokens(); i++) {
        myStrings[i] = tokenizer.nextToken();
      }
    }
    else {
      myStrings = null;
    }
  }

  public void setIconTextGap(int iconTextGap) {
    myIconTextGap = iconTextGap;
  }

  public int getIconTextGap() {
    return myIconTextGap;
  }

  public int getIconHeight() {
    return myIcon.getIconHeight() + getTextHeight() + myIconTextGap;
  }

  public int getIconWidth() {
    return Math.max(myIcon.getIconWidth(), getTextWidth());
  }

  private int getTextHeight() {
    if (myStrings != null) {
      Font font = UIUtil.getLabelFont();
      FontMetrics fontMetrics = Toolkit.getDefaultToolkit().getFontMetrics(font);
      return fontMetrics.getHeight() * myStrings.length;
    }
    else {
      return 0;
    }
  }

  private int getTextWidth() {
    if (myStrings != null) {
      int width = 0;
      Font font = UIUtil.getLabelFont();
      FontMetrics fontMetrics = Toolkit.getDefaultToolkit().getFontMetrics(font);
      for (String string : myStrings) {
        width = fontMetrics.stringWidth(string);
      }

      if (myMnemonic != null) {
        width += fontMetrics.stringWidth(myMnemonic);
      }
      return width;
    }
    else {
      return 0;
    }
  }

  public void paintIcon(Component c, Graphics g, int x, int y) {
    // Draw icon
    int width = getIconWidth();
    int iconWidth = myIcon.getIconWidth();
    if (width > iconWidth) {
      myIcon.paintIcon(c, g, x + (width - iconWidth) / 2, y);
    }
    else {
      myIcon.paintIcon(c, g, x, y);
    }
    // Draw text
    if (myStrings != null) {
      Font font = UIUtil.getLabelFont();
      FontMetrics fontMetrics = Toolkit.getDefaultToolkit().getFontMetrics(font);
      g.setFont(fontMetrics.getFont());
      if (myMnemonic != null) {
        width -= fontMetrics.stringWidth(myMnemonic);
      }
      g.setColor(UIUtil.getLabelForeground());
      y += myIcon.getIconHeight() + fontMetrics.getMaxAscent() + myIconTextGap;
      for (String string : myStrings) {
        g.drawString(string, x + (width - fontMetrics.stringWidth(string)) / 2, y);
        y += fontMetrics.getHeight();
      }

      if (myMnemonic != null) {
        y -= fontMetrics.getHeight();
        g.setColor(UIUtil.getTextInactiveTextColor());
        int offset = getTextWidth() - fontMetrics.stringWidth(myMnemonic);
        g.drawString(myMnemonic, x + offset, y);
      }
    }
  }
}
