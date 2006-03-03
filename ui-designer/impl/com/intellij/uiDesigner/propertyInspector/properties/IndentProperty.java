package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public class IndentProperty extends AbstractIntProperty<RadComponent> {
  public static IndentProperty getInstance(Project project) {
    return project.getComponent(IndentProperty.class);
  }

  public IndentProperty() {
    super(null, "Indent", 0);
  }

  public Object getValue(RadComponent component) {
    return component.getConstraints().getIndent();
  }

  protected void setValueImpl(RadComponent component, Object value) throws Exception {
    final int indent = ((Integer)value).intValue();

    final GridConstraints constraints = component.getConstraints();
    if (constraints.getIndent() != indent) {
      GridConstraints oldConstraints = (GridConstraints)constraints.clone();
      constraints.setIndent(indent);
      component.fireConstraintsChanged(oldConstraints);
    }
  }
}
