// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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


public class RadToolBar extends RadContainer {
  public static class Factory extends RadComponentFactory {
    @Override
    public RadComponent newInstance(ModuleProvider module, Class aClass, String id) {
      return new RadToolBar(module, aClass, id);
    }

    @Override
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

  @Override
  public void write(final XmlWriter writer) {
    writer.startElement(UIFormXmlConstants.ELEMENT_TOOLBAR);
    try {
      writeNoLayout(writer, JToolBar.class.getName());
    } finally {
      writer.endElement();
    }
  }

  private class RadToolBarLayoutManager extends RadAbstractIndexedLayoutManager {

    @Override
    @Nullable public String getName() {
      return null;
    }

    @Override @NotNull
    public ComponentDropLocation getDropLocation(RadContainer container, @Nullable final Point location) {
      return new FlowDropLocation(RadToolBar.this, location, FlowLayout.LEFT, 0, 0);
    }
  }
}
