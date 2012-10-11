/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.utils;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class SizedIcon implements Icon {
  private final int myWidth;
  private final int myHeight;
  private final Image myImage;

  public SizedIcon(int maxWidth, int maxHeight, Image image) {
    myWidth = Math.min(maxWidth, image.getWidth(null));
    myHeight = Math.min(maxHeight, image.getHeight(null));
    myImage = image;
  }

  public SizedIcon(int maxWidth, int maxHeight, ImageIcon icon) {
    this(maxWidth, maxHeight, icon.getImage());
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    g.drawImage(myImage, x, y, myWidth, myHeight, null);
  }

  @Override
  public int getIconWidth() {
    return myWidth;
  }

  @Override
  public int getIconHeight() {
    return myHeight;
  }
}