/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.util;

import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.SoftFactoryMap;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * @author spleaner
 */
public class ColorIconCache {
  private static final ColorIconCache INSTANCE = new ColorIconCache();
  private static final SoftFactoryMap<Color, Map<Integer, Icon>> ourCache = new SoftFactoryMap<Color, Map<Integer, Icon>>() {
    @Override
    protected Map<Integer, Icon> create(Color key) {
      return new HashMap<>();
    }
  };

  private ColorIconCache() {
  }

  public static ColorIconCache getIconCache() {
    return INSTANCE;
  }

  public Icon getIcon(@NotNull final Color color, final int size) {
    Icon icon = ourCache.get(color).get(size);
    if (icon == null) {
      icon = new ColorIcon(size, color);
      ourCache.get(color).put(size, icon);
    }

    return icon;
  }

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
      g.setColor(Color.BLACK);
      g.drawRect(i, j, iconWidth-1, iconHeight-1);
      ((Graphics2D)g).setComposite(old);
    }
  }
}
