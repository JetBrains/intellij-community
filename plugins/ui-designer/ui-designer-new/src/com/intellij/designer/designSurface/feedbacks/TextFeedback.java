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
package com.intellij.designer.designSurface.feedbacks;

import com.intellij.ui.Colors;
import com.intellij.ui.LightColors;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class TextFeedback extends SimpleColoredComponent {
  private static SimpleTextAttributes DIMENSION_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.lightGray);
  private static SimpleTextAttributes SNAP_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Colors.DARK_GREEN);

  public TextFeedback() {
    setBackground(LightColors.YELLOW);
  }

  public void bold(String text) {
    append(text, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
  }

  public void dimension(String text) {
    append(text, DIMENSION_ATTRIBUTES);
  }

  public void snap(String text) {
    append(text, SNAP_ATTRIBUTES);
  }

  public void centerTop(Rectangle bounds) {
    Dimension textSize = getPreferredSize();
    setBounds(bounds.x + bounds.width / 2 - textSize.width / 2, bounds.y - textSize.height - 10, textSize.width, textSize.height);
  }

  public void locationTo(Point location, int shift) {
    Dimension textSize = getPreferredSize();
    setBounds(location.x + shift, location.y + shift, textSize.width, textSize.height);
  }
}