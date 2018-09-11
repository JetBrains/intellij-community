// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.core.GridLayoutManager;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class SameSizeHorizontallyProperty extends AbstractGridLayoutProperty {
  public static SameSizeHorizontallyProperty getInstance(Project project) {
    return ServiceManager.getService(project, SameSizeHorizontallyProperty.class);
  }

  public SameSizeHorizontallyProperty(){
    super(null,"Same Size Horizontally");
  }

  @Override
  protected boolean getGridLayoutPropertyValue(GridLayoutManager gridLayoutManager) {
    return gridLayoutManager.isSameSizeHorizontally();
  }

  @Override
  protected void setGridLayoutPropertyValue(GridLayoutManager gridLayoutManager, boolean booleanValue) {
    gridLayoutManager.setSameSizeHorizontally(booleanValue);
  }
}
