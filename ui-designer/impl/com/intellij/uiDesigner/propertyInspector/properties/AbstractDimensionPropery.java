package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
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
public abstract class AbstractDimensionPropery extends Property {
  private final Property[] myChildren;
  private final DimensionRenderer myRenderer;

  public AbstractDimensionPropery(@NonNls final String name){
    super(null, name);
    myChildren=new Property[]{
      new MyWidthProperty(this),
      new MyHeightProperty(this)
    };
    myRenderer=new DimensionRenderer();
  }

  @NotNull
  public final Property[] getChildren(){
    return myChildren;
  }

  @NotNull
  public final PropertyRenderer getRenderer(){
    return myRenderer;
  }

  /**
   * This is not editable property (but it's children are editable)
   */
  public final PropertyEditor getEditor(){
    return null;
  }

  public Object getValue(RadComponent component) {
    return getValueImpl(component.getConstraints());
  }

  protected abstract Dimension getValueImpl(final GridConstraints constraints);

  @Override public boolean isModified(final RadComponent component) {
    final Palette palette = Palette.getInstance(component.getModule().getProject());
    final ComponentItem item = palette.getItem(component.getComponentClassName());
    assert item != null;
    return !getValueImpl(component.getConstraints()).equals(getValueImpl(item.getDefaultConstraints()));
  }

  /**
   * Child sub property which describe dimension's width
   */
  public final static class MyWidthProperty extends AbstractIntProperty {
    public MyWidthProperty(final Property parent){
      super(parent, "width", -1);
    }

    public Object getValue(final RadComponent component){
      final Dimension dimension = (Dimension)getParent().getValue(component);
      return new Integer(dimension.width);
    }

    protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
      final Dimension dimension=(Dimension)getParent().getValue(component);
      dimension.width = ((Integer)value).intValue();
      getParent().setValue(component, dimension);
    }
  }

  /**
   * Child sub property which describe dimension's height
   */
  public final static class MyHeightProperty extends AbstractIntProperty {
    public MyHeightProperty(final Property parent) {
      super(parent, "height", -1);
    }

    public Object getValue(final RadComponent component){
      final Dimension dimension = (Dimension)getParent().getValue(component);
      return new Integer(dimension.height);
    }

    protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
      final Dimension dimension = (Dimension)getParent().getValue(component);
      dimension.height = ((Integer)value).intValue();
      getParent().setValue(component, dimension);
    }
  }
}
