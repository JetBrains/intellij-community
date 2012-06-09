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

import com.intellij.uiDesigner.ModuleProvider;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.designSurface.ComponentDropLocation;
import com.intellij.uiDesigner.palette.Palette;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class RadToolBar extends RadContainer {
  public static class Factory extends RadComponentFactory {
    public RadComponent newInstance(ModuleProvider module, Class aClass, String id) {
      return new RadToolBar(module, aClass, id);
    }

    public RadComponent newInstance(final Class componentClass, final String id, final Palette palette) {
      return new RadToolBar(componentClass, id, palette);
    }
  }

  public RadToolBar(final ModuleProvider module, final Class componentClass, final String id) {
    super(module, componentClass, id);
  }

  public RadToolBar(Class componentClass, final String id, final Palette palette) {
    super(componentClass, id, palette);
  }

  @Override @Nullable
  protected RadLayoutManager createInitialLayoutManager() {
    return new RadToolBarLayoutManager();
  }

  public void write(final XmlWriter writer) {
    writer.startElement(UIFormXmlConstants.ELEMENT_TOOLBAR);
    try {
      writeNoLayout(writer, JToolBar.class.getName());
    } finally {
      writer.endElement();
    }
  }

  private class RadToolBarLayoutManager extends RadAbstractIndexedLayoutManager {

    @Nullable public String getName() {
      return null;
    }

    @Override @NotNull
    public ComponentDropLocation getDropLocation(RadContainer container, @Nullable final Point location) {
      return new FlowDropLocation(RadToolBar.this, location, FlowLayout.LEFT, 0, 0);
    }
  }
}
