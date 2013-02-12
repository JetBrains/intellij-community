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

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class AbstractTextFeedback extends SimpleColoredComponent {
  public final void bold(String text) {
    append(text, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
  }

  public final void centerTop(Rectangle bounds) {
    Dimension textSize = getPreferredSize();
    setBounds(bounds.x + bounds.width / 2 - textSize.width / 2, bounds.y - textSize.height - 10, textSize.width, textSize.height);
  }

  public final void locationTo(Point location, int shift) {
    Dimension textSize = getPreferredSize();
    setBounds(location.x + shift, location.y + shift, textSize.width, textSize.height);
  }
}