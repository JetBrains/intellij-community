// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.designSurface.ComponentDropLocation;
import com.intellij.uiDesigner.designSurface.NoDropLocation;
import com.intellij.uiDesigner.inspections.FormInspectionUtil;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.LwContainer;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Design-time support for a layout manager.
 */
public abstract class RadLayoutManager {
  /**
   * Returns the name of the layout manager. If null is returned, the layout manager property is not
   * shown by the user.
   *
   * @return the layout manager name.
   */
  @Nullable
  public abstract String getName();

  @Nullable
  public LayoutManager createLayout() {
    return null;
  }

  public void readLayout(LwContainer lwContainer, RadContainer radContainer) throws Exception {
  }

  public void changeContainerLayout(RadContainer container) throws IncorrectOperationException {
    ensureChildrenVisible(container);
    container.setLayoutManager(this);
  }

  public abstract void writeChildConstraints(final XmlWriter writer, final RadComponent child);

  public void writeLayout(final XmlWriter writer, final RadContainer radContainer) {
  }

  public void refresh(RadContainer container) {
  }

  @NotNull
  public ComponentDropLocation getDropLocation(RadContainer container, @Nullable final Point location) {
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

  public boolean isIndexed() {
    return false;
  }

  public boolean isGrid() {
    return false;
  }

  public boolean areChildrenExclusive() {
    return false;
  }

  public void setChildDragging(RadComponent child, boolean dragging) {
    child.getDelegee().setVisible(!dragging);
  }

  public boolean canMoveComponent(RadComponent c, int rowDelta, int colDelta, final int rowSpanDelta, final int colSpanDelta) {
    return false;
  }

  public void moveComponent(RadComponent c, int rowDelta, int colDelta, final int rowSpanDelta, final int colSpanDelta) {
  }

  protected static void ensureChildrenVisible(final RadContainer container) {
    if (container.getLayoutManager().areChildrenExclusive()) {
      // ensure that components which were hidden by previous layout are visible (IDEADEV-16077)
      for (RadComponent child : container.getComponents()) {
        final IProperty property = FormInspectionUtil.findProperty(child, SwingProperties.VISIBLE);
        if (property == null || property.getPropertyValue(child) == Boolean.TRUE) {
          child.getDelegee().setVisible(true);
        }
      }
    }
  }
}
