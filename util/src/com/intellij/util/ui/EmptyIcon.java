/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.util.ui;

import javax.swing.*;
import java.awt.*;

public class EmptyIcon implements Icon {
  private final int myWidth;
  private final int myHeight;

  public EmptyIcon(int size) {
    this(size, size);
  }

  public EmptyIcon(int width, int height) {
    myWidth = width;
    myHeight = height;
  }

  public int getIconWidth() {
    return myWidth;
  }

  public int getIconHeight() {
    return myHeight;
  }

  public void paintIcon(Component component, Graphics g, int i, int j) {
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EmptyIcon)) return false;

    final EmptyIcon icon = (EmptyIcon)o;

    if (myHeight != icon.myHeight) return false;
    if (myWidth != icon.myWidth) return false;

    return true;
  }

  public int hashCode() {
    return myWidth + myHeight;
  }
}
