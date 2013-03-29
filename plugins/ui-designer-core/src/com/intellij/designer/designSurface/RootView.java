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
package com.intellij.designer.designSurface;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author Alexander Lobas
 */
public class RootView extends JComponent {
  protected int myX;
  protected int myY;
  protected BufferedImage myImage;

  public RootView(int x, int y, BufferedImage image) {
    updateLocation(x, y);
    setImage(image);
  }

  public RootView(int x, int y) {
    updateLocation(x, y);
  }

  public void updateLocation(int x, int y) {
    myX = x;
    myY = y;
  }

  public BufferedImage getImage() {
    return myImage;
  }

  public final void setImage(BufferedImage image) {
    setImage(image, myX, myY, image.getWidth(), image.getHeight());
  }

  public void setImage(BufferedImage image, int x, int y, int width, int height) {
    myImage = image;
    setBounds(x, y, width, height);
    repaint();
  }

  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    g.drawImage(myImage, 0, 0, null);
  }
}