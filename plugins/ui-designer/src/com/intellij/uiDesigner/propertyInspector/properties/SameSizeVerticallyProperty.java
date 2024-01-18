// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.core.GridLayoutManager;

@Service(Service.Level.PROJECT)
public final class SameSizeVerticallyProperty extends AbstractGridLayoutProperty {
  public static SameSizeVerticallyProperty getInstance(Project project) {
    return project.getService(SameSizeVerticallyProperty.class);
  }

  public SameSizeVerticallyProperty(){
    super(null,"Same Size Vertically");
  }

  @Override
  protected boolean getGridLayoutPropertyValue(final GridLayoutManager gridLayoutManager) {
    return gridLayoutManager.isSameSizeVertically();
  }

  @Override
  protected void setGridLayoutPropertyValue(final GridLayoutManager gridLayoutManager, final boolean booleanValue) {
    gridLayoutManager.setSameSizeVertically(booleanValue);
  }
}
