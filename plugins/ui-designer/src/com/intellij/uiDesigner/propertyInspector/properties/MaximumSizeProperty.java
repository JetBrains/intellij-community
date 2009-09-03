package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class MaximumSizeProperty extends AbstractDimensionProperty<RadComponent> {
  public static MaximumSizeProperty getInstance(Project project) {
    return ServiceManager.getService(project, MaximumSizeProperty.class);
  }

  public MaximumSizeProperty(){
    super("Maximum Size");
  }

  protected Dimension getValueImpl(final GridConstraints constraints) {
    return constraints.myMaximumSize;
  }

  protected void setValueImpl(final RadComponent component, final Dimension value) throws Exception{
    component.getConstraints().myMaximumSize.setSize(value);
  }
}
