package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.IntRenderer;

import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntroIntProperty extends IntrospectedProperty {
  private final PropertyRenderer myRenderer;
  private final PropertyEditor myEditor;

  public IntroIntProperty(final String name, final Method readMethod, final Method writeMethod){
    this(name, readMethod, writeMethod, new IntRenderer(), new IntEditor(Integer.MIN_VALUE));
  }

  public IntroIntProperty(
    final String name,
    final Method readMethod,
    final Method writeMethod,
    final PropertyRenderer renderer,
    final PropertyEditor editor
  ){
    super(name, readMethod, writeMethod);
    myRenderer = renderer;
    myEditor = editor;
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

  public void write(final Object value, final XmlWriter writer){
    writer.addAttribute("value", ((Integer)value).intValue());
  }
}
