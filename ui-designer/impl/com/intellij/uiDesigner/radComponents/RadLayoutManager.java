/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.snapShooter.SnapshotContext;
import com.intellij.uiDesigner.designSurface.DropLocation;
import com.intellij.uiDesigner.designSurface.NoDropLocation;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.openapi.project.Project;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

/**
 * Design-time support for a layout manager.
 *
 * @author yole
 */
public abstract class RadLayoutManager {
  @NonNls private static Map<String, Class<? extends RadLayoutManager>> ourLayoutManagerRegistry = new HashMap<String, Class<? extends RadLayoutManager>>();
  @NonNls private static Map<Class, Class<? extends RadLayoutManager>> ourLayoutManagerClassRegistry = new HashMap<Class, Class<? extends RadLayoutManager>>();

  static {
    ourLayoutManagerRegistry.put(UIFormXmlConstants.LAYOUT_INTELLIJ, RadGridLayoutManager.class);
    ourLayoutManagerRegistry.put(UIFormXmlConstants.LAYOUT_GRIDBAG, RadGridBagLayoutManager.class);
    ourLayoutManagerRegistry.put(UIFormXmlConstants.LAYOUT_BORDER, RadBorderLayoutManager.class);
    ourLayoutManagerRegistry.put(UIFormXmlConstants.LAYOUT_FLOW, RadFlowLayoutManager.class);
    ourLayoutManagerRegistry.put(UIFormXmlConstants.LAYOUT_XY, RadXYLayoutManager.class);
    ourLayoutManagerRegistry.put(UIFormXmlConstants.LAYOUT_CARD, RadCardLayoutManager.class);

    ourLayoutManagerClassRegistry.put(BorderLayout.class, RadBorderLayoutManager.class);
    ourLayoutManagerClassRegistry.put(GridBagLayout.class, RadGridBagLayoutManager.class);
    ourLayoutManagerClassRegistry.put(FlowLayout.class, RadFlowLayoutManager.class);
    ourLayoutManagerClassRegistry.put(GridLayout.class, RadSwingGridLayoutManager.class);
    ourLayoutManagerClassRegistry.put(BoxLayout.class, RadBoxLayoutManager.class);
    ourLayoutManagerClassRegistry.put(CardLayout.class, RadCardLayoutManager.class);
  }

  public static String[] getLayoutManagerNames() {
    final String[] layoutManagerNames = ourLayoutManagerRegistry.keySet().toArray(new String[0]);
    Arrays.sort(layoutManagerNames);
    return layoutManagerNames;
  }

  public static RadLayoutManager createLayoutManager(String name) throws IllegalAccessException, InstantiationException {
    Class<? extends RadLayoutManager> cls = ourLayoutManagerRegistry.get(name);
    if (cls == null) {
      throw new IllegalArgumentException("Unknown layout manager " + name);
    }
    return cls.newInstance();
  }

  public static RadLayoutManager createFromLayout(LayoutManager layout) {
    for(Map.Entry<Class, Class<? extends RadLayoutManager>> e: ourLayoutManagerClassRegistry.entrySet()) {
      if (e.getKey().isInstance(layout)) {
        try {
          return e.getValue().newInstance();
        }
        catch (Exception ex) {
          throw new RuntimeException(ex);
        }
      }
    }
    return null;
  }

  public static boolean isKnownLayoutClass(String className) {
    for(Class c: ourLayoutManagerClassRegistry.keySet()) {
      if (c.getName().equals(className)) {
        return true;
      }
    }
    return false;
  }

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
    return new NoDropLocation();
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
}
