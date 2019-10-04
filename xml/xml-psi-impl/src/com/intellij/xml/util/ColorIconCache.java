// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util;

import com.intellij.util.containers.SoftFactoryMap;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author spleaner
 */
public class ColorIconCache {
  private static final ColorIconCache INSTANCE = new ColorIconCache();
  private static final SoftFactoryMap<Color, Map<Integer, Icon>> ourCache = new SoftFactoryMap<Color, Map<Integer, Icon>>() {
    @Override
    protected Map<Integer, Icon> create(Color key) {
      return new ConcurrentHashMap<>();
    }
  };

  private ColorIconCache() { }

  public static ColorIconCache getIconCache() {
    return INSTANCE;
  }

  public Icon getIcon(@NotNull Color color, int size) {
    return Objects.requireNonNull(ourCache.get(color)).computeIfAbsent(size, s -> new com.intellij.util.ui.ColorIcon(s, color, true));
  }

  /**
   * @deprecated use {@link com.intellij.util.ui.ColorIcon} instead
   */
  @Deprecated
  public static class ColorIcon extends EmptyIcon {
    private Color myColor;
    private Color[] myColours;

    public ColorIcon(final int size, final Color color) {
      super(size);
      myColor = color;
    }

    public ColorIcon(final int size, final Color[] colours) {
      super(size);
      myColours = colours;
    }

    protected ColorIcon(ColorIcon icon) {
      super(icon);
      myColor = icon.myColor;
      if (icon.myColours != null) myColours = Arrays.copyOf(icon.myColours, icon.myColours.length);
    }

    @NotNull
    @Override
    public ColorIcon copy() {
      return new ColorIcon(this);
    }

    public Color getColor() {
      return myColor;
    }

    public Color[] getColours() {
      return myColours;
    }

    @Override
    public void paintIcon(final Component component, final Graphics g, final int i, final int j) {
      final int iconWidth = getIconWidth();
      final int iconHeight = getIconHeight();
      if (myColor != null) {
        g.setColor(myColor);
        g.fillRect(i, j, iconWidth, iconHeight);
      }
      else if (myColours != null) {
        final Color top = myColours[0];
        g.setColor(top);
        g.fillRect(i, j, iconWidth, 2);

        final Color right = myColours[1];
        g.setColor(right);
        g.fillRect(i + iconWidth / 2, j + 2, iconWidth / 2, iconHeight / 2);

        final Color bottom = myColours[2];
        g.setColor(bottom);
        g.fillRect(i, j + iconHeight - 2, iconWidth, 2);

        final Color left = myColours[3];
        g.setColor(left);
        g.fillRect(i, j + 2, iconWidth / 2, iconHeight / 2);
      }

      final Composite old = ((Graphics2D)g).getComposite();
      ((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.1f));
      //noinspection UseJBColor
      g.setColor(Color.BLACK);
      g.drawRect(i, j, iconWidth-1, iconHeight-1);
      ((Graphics2D)g).setComposite(old);
    }
  }
}