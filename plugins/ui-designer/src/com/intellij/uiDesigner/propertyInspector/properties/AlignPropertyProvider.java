// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.radComponents.RadComponent;


public interface AlignPropertyProvider {
  int getAlignment(RadComponent component, boolean horizontal);
  void setAlignment(RadComponent component, boolean horizontal, int alignment);
  void resetAlignment(RadComponent component, boolean horizontal);
  boolean isAlignmentModified(RadComponent component, boolean horizontal);
}
