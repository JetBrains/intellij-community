package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.DoubleEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.DoubleRenderer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntroDoubleProperty extends IntrospectedProperty<Double> {
  private final DoubleRenderer myRenderer;
  private final DoubleEditor myEditor;

  public IntroDoubleProperty(final String name, final Method readMethod, final Method writeMethod){
    super(name, readMethod, writeMethod);
    myRenderer = new DoubleRenderer();
    myEditor = new DoubleEditor();
  }

  @NotNull
  public PropertyRenderer<Double> getRenderer(){
    return myRenderer;
  }

  public PropertyEditor<Double> getEditor(){
    return myEditor;
  }

  public void write(final Double value, final XmlWriter writer){
    writer.addAttribute("value", value.toString());
  }
}
