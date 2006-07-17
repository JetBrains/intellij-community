/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.designSurface.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class RadToolBar extends RadContainer {
  public static class Factory extends RadComponentFactory {
    public RadComponent newInstance(Module module, Class aClass, String id) {
      return new RadToolBar(module, aClass, id);
    }

    public RadComponent newInstance(final Class componentClass, final String id, final Palette palette) {
      return new RadToolBar(componentClass, id, palette);
    }
  }

  public RadToolBar(final Module module, final Class componentClass, final String id) {
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

  private class RadToolBarLayoutManager extends RadLayoutManager {

    @Nullable public String getName() {
      return null;
    }

    public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
    }

    public void addComponentToContainer(final RadContainer container, final RadComponent component, final int index) {
      getDelegee().add(component.getDelegee(), component.getConstraints(), index);
    }

    @Override @NotNull
    public DropLocation getDropLocation(RadContainer container, @Nullable final Point location) {
      return new FlowDropLocation(RadToolBar.this, location, FlowLayout.LEFT, 0, 0);
    }

    @Override
    public boolean isIndexed() {
      return true;
    }
  }
}
