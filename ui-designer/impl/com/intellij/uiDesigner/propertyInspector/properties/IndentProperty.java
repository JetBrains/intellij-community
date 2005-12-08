package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.core.GridConstraints;

/**
 * @author yole
 */
public class IndentProperty extends AbstractIntProperty {
  public IndentProperty() {
    super(null, "Indent", 0);
  }

  public Object getValue(RadComponent component) {
    return new Integer(component.getConstraints().getIndent());
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
