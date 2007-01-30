package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class SameSizeVerticallyProperty extends AbstractGridLayoutProperty {
  public static SameSizeVerticallyProperty getInstance(Project project) {
    return ServiceManager.getService(project, SameSizeVerticallyProperty.class);
  }

  public SameSizeVerticallyProperty(){
    super(null,"Same Size Vertically");
  }

  protected boolean getGridLayoutPropertyValue(final GridLayoutManager gridLayoutManager) {
    return gridLayoutManager.isSameSizeVertically();
  }

  protected void setGridLayoutPropertyValue(final GridLayoutManager gridLayoutManager, final boolean booleanValue) {
    gridLayoutManager.setSameSizeVertically(booleanValue);
  }
}
