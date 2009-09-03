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
public final class PreferredSizeProperty extends AbstractDimensionProperty<RadComponent> {
  public static PreferredSizeProperty getInstance(Project project) {
    return ServiceManager.getService(project, PreferredSizeProperty.class);
  }

  public PreferredSizeProperty(){
    super("Preferred Size");
  }

  protected Dimension getValueImpl(final GridConstraints constraints) {
    return constraints.myPreferredSize;
  }

  protected void setValueImpl(final RadComponent component, final Dimension value) throws Exception{
    component.getConstraints().myPreferredSize.setSize(value);
  }
}
