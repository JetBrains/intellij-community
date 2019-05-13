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

import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.GuiDesignerConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.jgoodies.forms.layout.FormLayout;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;

/**
 * @author yole
 */
public class LayoutManagerRegistry {
  @NonNls private static final Map<String, Class<? extends RadLayoutManager>> ourLayoutManagerRegistry = new HashMap<>();
  @NonNls private static final Map<Class, Class<? extends RadLayoutManager>> ourLayoutManagerClassRegistry = new HashMap<>();
  @NonNls private static final Map<String, String> ourLayoutManagerDisplayNames = new HashMap<>();

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
    final String[] layoutManagerNames = ArrayUtil.toStringArray(ourLayoutManagerRegistry.keySet());
    Arrays.sort(layoutManagerNames);
    return layoutManagerNames;
  }

  public static String[] getNonDeprecatedLayoutManagerNames() {
    ArrayList<String> layoutManagerNames = new ArrayList<>();
    for(String name: ourLayoutManagerRegistry.keySet()) {
      if (!name.equals(UIFormXmlConstants.LAYOUT_XY)) {
        layoutManagerNames.add(name);
      }
    }
    Collections.sort(layoutManagerNames);
    return ArrayUtil.toStringArray(layoutManagerNames);
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

  @Nullable
  public static RadLayoutManager createFromLayout(LayoutManager layout) {
    if (layout == null) return null;
    return createFromLayoutClass(layout.getClass());
  }

  @Nullable
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

  public static RadLayoutManager createDefaultGridLayoutManager(Project project) {
    final String defaultLayoutManager = GuiDesignerConfiguration.getInstance(project).DEFAULT_LAYOUT_MANAGER;
    if (defaultLayoutManager.equals(UIFormXmlConstants.LAYOUT_GRIDBAG)) {
      return new RadGridBagLayoutManager();
    }
    else if (defaultLayoutManager.equals(UIFormXmlConstants.LAYOUT_FORM)) {
      return new RadFormLayoutManager();
    }
    else {
      return new RadGridLayoutManager();
    }
  }
}
