package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.IntRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class IndentProperty extends Property {
  private final IntRenderer myRenderer = new IntRenderer();
  private final IntEditor myEditor = new IntEditor(0);

  public IndentProperty() {
    super(null, "Indent");
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

  @NotNull public PropertyRenderer getRenderer() {
    return myRenderer;
  }

  @Nullable public PropertyEditor getEditor() {
    return myEditor;
  }
}
