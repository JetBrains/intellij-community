
package com.intellij.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.impl.ElementBase;
import com.intellij.util.Icons;
import com.intellij.util.SmartList;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class RowIcon implements Icon {
  private static final Icon STATIC_MARK_ICON = IconLoader.getIcon("/nodes/staticMark.png");
  private static final Icon FINAL_MARK_ICON = IconLoader.getIcon("/nodes/finalMark.png");
  private static final Icon JUNIT_TEST_MARK = IconLoader.getIcon("/nodes/junitTestMark.png");

  public static final int HORIZONTAL = 1;
  public static final int VERTICAL = 2;
  private Icon[] myIcons;
  private int myWidth;
  private int myHeight;

  public RowIcon(int iconCount/*, int orientation*/) {
    myIcons = new Icon[iconCount];
    //myOrientation = orientation;
  }

  public int hashCode() {
    return myIcons.length > 0 ? myIcons[0].hashCode() : 0;
  }

  public boolean equals(Object obj) {
    return obj instanceof RowIcon && Arrays.equals(((RowIcon)obj).myIcons, myIcons);
  }

  public int getIconCount() {
    return myIcons.length;
  }

  public void setIcon(Icon icon, int layer) {
    myIcons[layer] = icon;
    recalculateSize();
  }

  public Icon getIcon(int index) {
    return myIcons[index];
  }

  public void paintIcon(Component c, Graphics g, int x, int y) {
    int _x = x;
    for (Icon icon : myIcons) {
      if (icon == null) continue;
      icon.paintIcon(c, g, _x, y);
      _x += icon.getIconWidth();
      //_y += icon.getIconHeight();
    }
  }

  public int getIconWidth() {
    return myWidth;
  }

  public int getIconHeight() {
    return myHeight;
  }

  private void recalculateSize() {
    int width = 0;
    int height = 0;
    for (Icon icon : myIcons) {
      if (icon == null) continue;
      width += icon.getIconWidth();
      //height += icon.getIconHeight();
      height = Math.max(height, icon.getIconHeight());
    }
    myWidth = width;
    myHeight = height;
  }

  public static RowIcon createLayeredIcon(Icon icon, int flags) {
    if (flags != 0) {
      List<Icon> iconLayers = new SmartList<Icon>();
      if ((flags & ElementBase.FLAGS_STATIC) != 0) {
        iconLayers.add(STATIC_MARK_ICON);
      }
      if ((flags & ElementBase.FLAGS_LOCKED) != 0) {
        iconLayers.add(Icons.LOCKED_ICON);
      }
      if ((flags & ElementBase.FLAGS_EXCLUDED) != 0) {
        iconLayers.add(Icons.EXCLUDED_FROM_COMPILE_ICON);
      }
      final boolean isFinal = (flags & ElementBase.FLAGS_FINAL) != 0;
      if (isFinal) {
        iconLayers.add(FINAL_MARK_ICON);
      }
      if ((flags & ElementBase.FLAGS_JUNIT_TEST) != 0) {
        iconLayers.add(JUNIT_TEST_MARK);
      }
      LayeredIcon layeredIcon = new LayeredIcon(1 + iconLayers.size());
      layeredIcon.setIcon(icon, 0);
      for (int i = 0; i < iconLayers.size(); i++) {
        Icon icon1 = iconLayers.get(i);
        layeredIcon.setIcon(icon1, i+1);
      }
      icon = layeredIcon;
    }
    RowIcon baseIcon = new RowIcon(2);
    baseIcon.setIcon(icon, 0);
    return baseIcon;
  }
}
