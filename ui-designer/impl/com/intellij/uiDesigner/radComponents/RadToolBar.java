/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.designSurface.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class RadToolBar extends RadContainer {
  public RadToolBar(final Module module, final String id) {
    super(module, JToolBar.class, id);
  }

  @Override @Nullable
  protected RadLayoutManager createInitialLayoutManager() {
    return new RadToolBarLayoutManager();
  }

  public void write(final XmlWriter writer) {
    writer.startElement(UIFormXmlConstants.ELEMENT_TOOLBAR);
    try {
      writeNoLayout(writer);
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
      return new FlowDropLocation(RadToolBar.this, location, 0, 0, true);
    }

    @Override
    public boolean isIndexed() {
      return true;
    }
  }
}
