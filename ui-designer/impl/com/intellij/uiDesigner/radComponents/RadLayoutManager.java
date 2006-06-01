/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.designSurface.DropLocation;
import com.intellij.uiDesigner.designSurface.NoDropLocation;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.snapShooter.SnapshotContext;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.LayoutManager;
import java.awt.Point;

/**
 * Design-time support for a layout manager.
 *
 * @author yole
 */
public abstract class RadLayoutManager {
  /**
   * Returns the name of the layout manager. If null is returned, the layout manager property is not
   * shown by the user.
   *
   * @return the layout manager name.
   */
  @Nullable public abstract String getName();

  @Nullable public LayoutManager createLayout() {
    return null;
  }

  public void changeContainerLayout(RadContainer container) throws IncorrectOperationException {
    container.setLayoutManager(this);
  }

  public abstract void writeChildConstraints(final XmlWriter writer, final RadComponent child);

  public void writeLayout(final XmlWriter writer, final RadContainer radContainer) {
  }

  @NotNull public DropLocation getDropLocation(RadContainer container, @Nullable final Point location) {
    return NoDropLocation.INSTANCE;
  }

  public abstract void addComponentToContainer(final RadContainer container, final RadComponent component, final int index);

  public void removeComponentFromContainer(final RadContainer container, final RadComponent component) {
    container.getDelegee().remove(component.getDelegee());
  }

  public boolean isSwitchedToChild(RadContainer container, RadComponent child) {
    return true;
  }

  public boolean switchContainerToChild(RadContainer container, RadComponent child) {
    return false;
  }

  public Property[] getContainerProperties(final Project project) {
    return Property.EMPTY_ARRAY;
  }

  public Property[] getComponentProperties(final Project project, final RadComponent component) {
    return Property.EMPTY_ARRAY;
  }

  public void addSnapshotComponent(final JComponent parent,
                                   final JComponent child,
                                   final RadContainer container,
                                   final RadComponent component) {
    throw new UnsupportedOperationException("Layout manager " + this + " does not support adding snapshot components");
  }

  public void createSnapshotLayout(final SnapshotContext context,
                                   final JComponent parent,
                                   final RadContainer container,
                                   final LayoutManager layout) {
  }

  public boolean isIndexed() {
    return false;
  }

  public boolean isGrid() {
    return false;
  }
}
