package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.BooleanEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.BooleanRenderer;

import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntroBooleanProperty extends IntrospectedProperty {
  private final BooleanRenderer myRenderer;
  private final BooleanEditor myEditor;

  public IntroBooleanProperty(final String name, final Method readMethod, final Method writeMethod){
    super(name, readMethod, writeMethod);
    myRenderer = new BooleanRenderer();
    myEditor = new BooleanEditor();
  }

  public Property[] getChildren(){
    return EMPTY_ARRAY;
  }

  public PropertyEditor getEditor(){
    return myEditor;
  }

  public void write(final Object value, final XmlWriter writer){
    writer.addAttribute("value", value.toString());
  }

  public PropertyRenderer getRenderer(){
    return myRenderer;
  }
}
