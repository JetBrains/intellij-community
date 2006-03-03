/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.designSurface.DropLocation;
import com.intellij.uiDesigner.designSurface.NoDropLocation;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.awt.LayoutManager;
import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

/**
 * Design-time support for a layout manager.
 *
 * @author yole
 */
public abstract class RadLayoutManager {
  @NonNls private static Map<String, Class<? extends RadLayoutManager>> ourLayoutManagerRegistry = new HashMap<String, Class<? extends RadLayoutManager>>();

  static {
    ourLayoutManagerRegistry.put(UIFormXmlConstants.LAYOUT_INTELLIJ, RadGridLayoutManager.class);
    ourLayoutManagerRegistry.put(UIFormXmlConstants.LAYOUT_GRIDBAG, RadGridBagLayoutManager.class);
    ourLayoutManagerRegistry.put(UIFormXmlConstants.LAYOUT_BORDER, RadBorderLayoutManager.class);
    ourLayoutManagerRegistry.put(UIFormXmlConstants.LAYOUT_FLOW, RadFlowLayoutManager.class);
  }

  public static String[] getLayoutManagerNames() {
    return ourLayoutManagerRegistry.keySet().toArray(new String[0]);
  }

  public static RadLayoutManager createLayoutManager(String name) throws IllegalAccessException, InstantiationException {
    Class<? extends RadLayoutManager> cls = ourLayoutManagerRegistry.get(name);
    if (cls == null) {
      throw new IllegalArgumentException("Unknown layout manager " + name);
    }
    return cls.newInstance();
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

  public boolean canChangeLayout(final RadContainer container) {
    return true;
  }

  public abstract void writeChildConstraints(final XmlWriter writer, final RadComponent child);

  public void writeLayout(final XmlWriter writer, final RadContainer radContainer) {
  }

  @NotNull public DropLocation getDropLocation(RadContainer container, @Nullable final Point location) {
    return new NoDropLocation();
  }

  public abstract void addComponentToContainer(final RadContainer container, final RadComponent component, final int index);

  public Property[] getContainerProperties(final Project project) {
    return Property.EMPTY_ARRAY;
  }

  public Property[] getComponentProperties(final Project project) {
    return Property.EMPTY_ARRAY;
  }
}
