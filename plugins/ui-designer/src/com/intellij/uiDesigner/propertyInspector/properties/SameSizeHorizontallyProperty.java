package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;

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

  protected boolean getGridLayoutPropertyValue(GridLayoutManager gridLayoutManager) {
    return gridLayoutManager.isSameSizeHorizontally();
  }

  protected void setGridLayoutPropertyValue(GridLayoutManager gridLayoutManager, boolean booleanValue) {
    gridLayoutManager.setSameSizeHorizontally(booleanValue);
  }
}
