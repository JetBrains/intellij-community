/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.UIFormXmlConstants;
import com.jgoodies.forms.layout.FormLayout;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class LayoutManagerRegistry {
  @NonNls private static Map<String, Class<? extends RadLayoutManager>> ourLayoutManagerRegistry = new HashMap<String, Class<? extends RadLayoutManager>>();
  @NonNls private static Map<Class, Class<? extends RadLayoutManager>> ourLayoutManagerClassRegistry = new HashMap<Class, Class<? extends RadLayoutManager>>();
  @NonNls private static Map<String, String> ourLayoutManagerDisplayNames = new HashMap<String, String>();

  private LayoutManagerRegistry() {
  }

  static {
    ourLayoutManagerRegistry.put(UIFormXmlConstants.LAYOUT_INTELLIJ, RadGridLayoutManager.class);
    ourLayoutManagerRegistry.put(UIFormXmlConstants.LAYOUT_GRIDBAG, RadGridBagLayoutManager.class);
    ourLayoutManagerRegistry.put(UIFormXmlConstants.LAYOUT_BORDER, RadBorderLayoutManager.class);
    ourLayoutManagerRegistry.put(UIFormXmlConstants.LAYOUT_FLOW, RadFlowLayoutManager.class);
    ourLayoutManagerRegistry.put(UIFormXmlConstants.LAYOUT_XY, RadXYLayoutManager.class);
    ourLayoutManagerRegistry.put(UIFormXmlConstants.LAYOUT_CARD, RadCardLayoutManager.class);
    ourLayoutManagerRegistry.put(UIFormXmlConstants.LAYOUT_FORM, RadFormLayoutManager.class);

    ourLayoutManagerClassRegistry.put(BorderLayout.class, RadBorderLayoutManager.class);
    ourLayoutManagerClassRegistry.put(GridBagLayout.class, RadGridBagLayoutManager.class);
    ourLayoutManagerClassRegistry.put(FlowLayout.class, RadFlowLayoutManager.class);
    ourLayoutManagerClassRegistry.put(GridLayout.class, RadSwingGridLayoutManager.class);
    ourLayoutManagerClassRegistry.put(BoxLayout.class, RadBoxLayoutManager.class);
    ourLayoutManagerClassRegistry.put(CardLayout.class, RadCardLayoutManager.class);
    ourLayoutManagerClassRegistry.put(FormLayout.class, RadFormLayoutManager.class);

    ourLayoutManagerDisplayNames.put(UIFormXmlConstants.LAYOUT_INTELLIJ, "GridLayoutManager (IntelliJ)");
    ourLayoutManagerDisplayNames.put(UIFormXmlConstants.LAYOUT_FORM, "FormLayout (JGoodies)");
  }

  public static String[] getLayoutManagerNames() {
    final String[] layoutManagerNames = ourLayoutManagerRegistry.keySet().toArray(new String[0]);
    Arrays.sort(layoutManagerNames);
    return layoutManagerNames;
  }

  public static String getLayoutManagerDisplayName(String name) {
    if (ourLayoutManagerDisplayNames.containsKey(name)) {
      return ourLayoutManagerDisplayNames.get(name);
    }
    return name;
  }

  public static RadLayoutManager createLayoutManager(String name) throws IllegalAccessException, InstantiationException {
    Class<? extends RadLayoutManager> cls = ourLayoutManagerRegistry.get(name);
    if (cls == null) {
      throw new IllegalArgumentException("Unknown layout manager " + name);
    }
    return cls.newInstance();
  }

  public static RadLayoutManager createFromLayout(LayoutManager layout) {
    return createFromLayoutClass(layout.getClass());
  }

  private static RadLayoutManager createFromLayoutClass(final Class aClass) {
    // we can't use isInstance() because the class in our map and aClass may have been loaded with
    // different classloaders
    for(Map.Entry<Class, Class<? extends RadLayoutManager>> e: ourLayoutManagerClassRegistry.entrySet()) {
      if (e.getKey().getName().equals(aClass.getName())) {
        try {
          return e.getValue().newInstance();
        }
        catch (Exception ex) {
          throw new RuntimeException(ex);
        }
      }
    }
    if (aClass.getSuperclass() != null) {
      return createFromLayoutClass(aClass.getSuperclass());
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
}
