package com.intellij.openapi.editor.impl;

import gnu.trove.TIntHashSet;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public class FontInfo {
  private String myFamilyName;
  private Font myFont;
  private int mySize;
  private int myStyle;
  private TIntHashSet mySafeCharacters = new TIntHashSet();
  private FontMetrics myFontMetrics = null;
  private int[] charWidth = new int[128];

  public FontInfo(final String familyName, final int size, final int style) {
    myFamilyName = familyName;
    mySize = size;
    myStyle = style;
    myFont = new Font(familyName, style, size);
  }

  public boolean canDisplay(char c) {
    try {
      if (c < 128) return true;
      if (mySafeCharacters.contains(c)) return true;
      if (myFont.canDisplay(c)) {
        mySafeCharacters.add(c);
        return true;
      }
      return false;
    }
    catch (Exception e) {
      // JRE has problems working with the font. Just skip.
      return false;
    }
  }

  public Font getFont() {
    return myFont;
  }

  public int charWidth(char c, JComponent anyComponent) {
    final FontMetrics metrics = fontMetrics(anyComponent);
    if (c < 128) return charWidth[c];
    return metrics.charWidth(c);
  }

  private FontMetrics fontMetrics(JComponent anyComponent) {
    if (myFontMetrics == null) {
      myFontMetrics = anyComponent.getFontMetrics(myFont);
      for (int i = 0; i < 128; i++) {
        charWidth[i] = myFontMetrics.charWidth(i);
      }
    }
    return myFontMetrics;
  }

  public int getSize() {
    return mySize;
  }

  public int getStyle() {
    return myStyle;
  }
}
