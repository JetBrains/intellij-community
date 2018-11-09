/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.designer.propertyTable.renderers;

import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static java.lang.Math.ceil;
import static java.lang.Math.floor;

/**
 * @author Alexander Lobas
 */
public final class ColorIcon extends EmptyIcon {
  private final int myColorSize;
  private Color myColor;
  private boolean myShowRedLine;

  public ColorIcon(int size, int colorSize) {
    super(size, size);
    myColorSize = colorSize;
  }

  protected ColorIcon(ColorIcon icon) {
    super(icon);
    myColorSize = icon.myColorSize;
    myColor = icon.myColor;
    myShowRedLine = icon.myShowRedLine;
  }

  @Override
  @NotNull
  public ColorIcon copy() {
    return new ColorIcon(this);
  }

  public Color getColor() {
    return myShowRedLine ? null : myColor;
  }

  public void setColor(Color color) {
    myColor = color;
  }

  public void showRedLine(boolean value) {
    myShowRedLine = value;
  }

  @Override
  public void paintIcon(Component component, Graphics g, final int left, final int top) {
    int iconWidth = getIconWidth();
    int iconHeight = getIconHeight();

    if (component instanceof SimpleColoredComponent) {
      SimpleColoredComponent coloredComponent = (SimpleColoredComponent)component;
      g.setColor(component.getBackground());
      g.fillRect(left - coloredComponent.getIpad().left, 0,
                 iconWidth + coloredComponent.getIpad().left + coloredComponent.getIconTextGap(), component.getHeight());
    }

    int x = left + (int)floor((iconWidth - scaleVal(myColorSize)) / 2);
    int y = top + (int)floor((iconHeight - scaleVal(myColorSize)) / 2);

    g.setColor(myColor);
    g.fillRect(x, y, (int)ceil(scaleVal(myColorSize)), (int)ceil(scaleVal(myColorSize)));

    if (myShowRedLine) {
      Graphics2D g2d = (Graphics2D)g;
      Object hint = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(JBColor.red);
      g.drawLine(x, y + (int)floor(scaleVal(myColorSize)), x + (int)floor(scaleVal(myColorSize)), y);
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, hint);
    }

    g.setColor(Color.BLACK);
    g.drawRect(x, y, (int)ceil(scaleVal(myColorSize)), (int)ceil(scaleVal(myColorSize)));
  }
}