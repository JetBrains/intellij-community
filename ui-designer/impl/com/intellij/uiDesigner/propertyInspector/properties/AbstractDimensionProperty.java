package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntRegexEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.DimensionRenderer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * This class is a base for implementing such properties
 * as "minimum size", "preferred size" and "maximum size".
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class AbstractDimensionProperty<T extends RadComponent> extends Property<T, Dimension> {
  private final Property[] myChildren;
  private final DimensionRenderer myRenderer;
  private final IntRegexEditor<Dimension> myEditor;

  public AbstractDimensionProperty(@NonNls final String name){
    super(null, name);
    myChildren=new Property[]{
      new IntFieldProperty(this, "width", -1, new Dimension(0, 0)),
      new IntFieldProperty(this, "height", -1, new Dimension(0, 0)),
    };
    myRenderer = new DimensionRenderer();
    myEditor = new IntRegexEditor<Dimension>(Dimension.class, myRenderer, new int[] { -1, -1 });
  }

  @NotNull
  public final Property[] getChildren(final RadComponent component){
    return myChildren;
  }

  @NotNull
  public final PropertyRenderer<Dimension> getRenderer() {
    return myRenderer;
  }

  public final PropertyEditor<Dimension> getEditor() {
    return myEditor;
  }

  public Dimension getValue(T component) {
    return getValueImpl(component.getConstraints());
  }

  protected abstract Dimension getValueImpl(final GridConstraints constraints);

  @Override public boolean isModified(final T component) {
    final Dimension defaultValue = getValueImpl(FormEditingUtil.getDefaultConstraints(component));
    return !getValueImpl(component.getConstraints()).equals(defaultValue);
  }

  @Override public void resetValue(T component) throws Exception {
    setValueImpl(component, getValueImpl(FormEditingUtil.getDefaultConstraints(component)));
  }
}
