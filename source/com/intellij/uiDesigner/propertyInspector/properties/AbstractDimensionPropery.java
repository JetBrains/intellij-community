package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.DimensionRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.IntRenderer;

import java.awt.*;

/**
 * This class is a base for implementing such properties
 * as "minimum size", "preferred size" and "maximum size".
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class AbstractDimensionPropery extends Property{
  private final Property[] myChildren;
  private final DimensionRenderer myRenderer;

  public AbstractDimensionPropery(final String name){
    super(null, name);
    myChildren=new Property[]{
      new MyWidthProperty(this),
      new MyHeightProperty(this)
    };
    myRenderer=new DimensionRenderer();
  }

  public final Property[] getChildren(){
    return myChildren;
  }

  public final PropertyRenderer getRenderer(){
    return myRenderer;
  }

  /**
   * This is not editable property (but it's children are editable)
   */
  public final PropertyEditor getEditor(){
    return null;
  }

  /**
   * Child sub property which describe dimension's width
   */
  public final static class MyWidthProperty extends Property{
    private final IntRenderer myRenderer;
    private final IntEditor myEditor;

    public MyWidthProperty(final Property parent){
      super(parent, "width");
      myRenderer = new IntRenderer();
      myEditor = new IntEditor(-1);
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

    public Property[] getChildren(){
      return EMPTY_ARRAY;
    }

    public PropertyRenderer getRenderer(){
      return myRenderer;
    }

    public PropertyEditor getEditor(){
      return myEditor;
    }
  }

  /**
   * Child sub property which describe dimension's height
   */
  public final static class MyHeightProperty extends Property{
    private final IntRenderer myRenderer;
    private final IntEditor myEditor;

    public MyHeightProperty(final Property parent){
      super(parent, "height");
      myRenderer = new IntRenderer();
      myEditor = new IntEditor(-1);
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

    public Property[] getChildren(){
      return EMPTY_ARRAY;
    }

    public PropertyRenderer getRenderer(){
      return myRenderer;
    }

    public PropertyEditor getEditor(){
      return myEditor;
    }
  }
}
