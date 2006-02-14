package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.core.GridLayoutManager;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class SameSizeHorizontallyProperty extends AbstractGridLayoutProperty {
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
