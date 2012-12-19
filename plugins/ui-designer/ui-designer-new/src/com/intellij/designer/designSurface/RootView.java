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
  private int myX;
  private int myY;
  protected BufferedImage myImage;

  public RootView(int x, int y, BufferedImage image) {
    myX = x;
    myY = y;
    setImage(image);
  }

  public BufferedImage getImage() {
    return myImage;
  }

  public void setImage(BufferedImage image) {
    myImage = image;
    setBounds(myX, myY, image.getWidth(), image.getHeight());
  }

  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    g.drawImage(myImage, 0, 0, null);
  }
}