package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.DimensionRenderer;

import java.awt.*;
import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntroDimensionProperty extends IntrospectedProperty {
  private final Property[] myChildren;
  private final DimensionRenderer myRenderer;

  public IntroDimensionProperty(final String name, final Method readMethod, final Method writeMethod){
    super(name, readMethod, writeMethod);
    myChildren = new Property[]{
      new AbstractDimensionPropery.MyWidthProperty(this),
      new AbstractDimensionPropery.MyHeightProperty(this)
    };
    myRenderer = new DimensionRenderer();
  }

  public void write(final Object value, final XmlWriter writer){
    final Dimension dimension = (Dimension)value;
    writer.addAttribute("width", dimension.width);
    writer.addAttribute("height", dimension.height);
  }

  public Property[] getChildren(){
    return myChildren;
  }

  public PropertyRenderer getRenderer(){
    return myRenderer;
  }

  public PropertyEditor getEditor(){
    return null;
  }
}
