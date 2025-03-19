// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.ModuleProvider;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.ComponentDragObject;
import com.intellij.uiDesigner.designSurface.ComponentDropLocation;
import com.intellij.uiDesigner.designSurface.FeedbackLayer;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.palette.Palette;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class RadScrollPane extends RadContainer {
  public static final Class COMPONENT_CLASS = JScrollPane.class;
  private static final Logger LOG = Logger.getInstance(RadScrollPane.class);

  public static class Factory extends RadComponentFactory {
    @Override
    public RadComponent newInstance(ModuleProvider module, Class aClass, String id) {
      return new RadScrollPane(module, aClass, id);
    }

    @Override
    public RadComponent newInstance(final Class componentClass, final String id, final Palette palette) {
      return new RadScrollPane(componentClass, id, palette);
    }
  }

  public RadScrollPane(final ModuleProvider module, final Class componentClass, final String id){
    super(module, componentClass, id);
  }

  public RadScrollPane(final Class componentClass, final String id, final Palette palette) {
    super(componentClass, id, palette);
  }

  @Override
  protected @NotNull RadLayoutManager createInitialLayoutManager() {
    return new RadScrollPaneLayoutManager();
  }

  @Override
  public void write(final XmlWriter writer) {
    writer.startElement(UIFormXmlConstants.ELEMENT_SCROLLPANE);
    try {
      writeNoLayout(writer, JScrollPane.class.getName());
    } finally {
      writer.endElement(); // scrollpane
    }
  }

  @Override public RadComponent getActionTargetComponent(RadComponent child) {
    return this;
  }

  private class RadScrollPaneLayoutManager extends RadLayoutManager {
    private MyDropLocation myDropLocation = null;

    @Override
    public @Nullable String getName() {
      return null;
    }

    @Override
    public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
    }

    @Override
    public @NotNull ComponentDropLocation getDropLocation(RadContainer container, final @Nullable Point location) {
      if (myDropLocation == null) {
        myDropLocation = new MyDropLocation();
      }
      return myDropLocation;
    }

    @Override
    public void addComponentToContainer(final RadContainer container, final RadComponent component, final int index) {
      try {
        final JScrollPane scrollPane = (JScrollPane)container.getDelegee();
        final JComponent delegee = component.getDelegee();
        delegee.setLocation(0,0);
        scrollPane.setViewportView(delegee);
      }
      catch (ClassCastException e) {
        LOG.info(e);
        LOG.info("container classloader=" + container.getDelegee().getClass().getClassLoader());
        LOG.info("component classloader=" + component.getDelegee().getClass().getClassLoader());
        throw e;
      }
    }

    @Override public void removeComponentFromContainer(final RadContainer container, final RadComponent component) {
      final JScrollPane scrollPane = (JScrollPane)container.getDelegee();
      scrollPane.setViewportView(null);
    }
  }

  private class MyDropLocation implements ComponentDropLocation {
    @Override
    public RadContainer getContainer() {
      return RadScrollPane.this;
    }

    @Override
    public boolean canDrop(ComponentDragObject dragObject) {
      return dragObject.getComponentCount() == 1 && getComponentCount() == 0;
    }

    @Override
    public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
      feedbackLayer.putFeedback(getDelegee(), new Rectangle(0, 0, getWidth(), getHeight()), getDisplayName());
    }

    @Override
    public void processDrop(GuiEditor editor,
                            RadComponent[] components,
                            GridConstraints[] constraintsToAdjust,
                            ComponentDragObject dragObject) {
      addComponent(components[0]);
    }

    @Override
    public @Nullable ComponentDropLocation getAdjacentLocation(Direction direction) {
      return null;
    }
  }
}
