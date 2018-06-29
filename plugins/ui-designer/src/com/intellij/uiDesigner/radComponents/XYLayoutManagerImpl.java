/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.shared.XYLayoutManager;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class XYLayoutManagerImpl extends XYLayoutManager {
  private final Dimension myPreferredSize;
  private static final Dimension MIN_SIZE = new Dimension(20,20);

  public XYLayoutManagerImpl(){
    myPreferredSize = new Dimension();
  }

  public void setPreferredSize(final Dimension size) {
    myPreferredSize.setSize(size);
  }

  public Dimension maximumLayoutSize(final Container container){
    return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

  public Dimension preferredLayoutSize(final Container container){
    return myPreferredSize;
  }

  public void layoutContainer(final Container parent){
  }
  
  public Dimension minimumLayoutSize(final Container container){
    final Container parent = container.getParent();
    if (!(parent instanceof JComponent)) {
      return MIN_SIZE;
    }
    final RadComponent component = (RadComponent)((JComponent)parent).getClientProperty(RadComponent.CLIENT_PROP_RAD_COMPONENT);
    if (component == null) {
      return MIN_SIZE;
    }

    // the following code prevents XYs placed in Grid from being shrunk
    final RadContainer radParent = component.getParent();
    if (radParent != null && (radParent.getLayoutManager().isGrid())) {
      return new Dimension(
        Math.max(myPreferredSize.width, MIN_SIZE.width),
        Math.max(myPreferredSize.height, MIN_SIZE.height)
      );
    }
    return MIN_SIZE;
  }
}
