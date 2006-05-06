/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.radComponents.RadComponent;

/**
 * @author yole
 */
public interface AlignPropertyProvider {
  int getAlignment(RadComponent component, boolean horizontal);
  void setAlignment(RadComponent component, boolean horizontal, int alignment);
  void resetAlignment(RadComponent component, boolean horizontal);
  boolean isAlignmentModified(RadComponent component, boolean horizontal);
}
