package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.BooleanEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.BooleanRenderer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntroBooleanProperty extends IntrospectedProperty<Boolean> {
  private final BooleanRenderer myRenderer;
  private final BooleanEditor myEditor;

  public IntroBooleanProperty(final String name, final Method readMethod, final Method writeMethod){
    super(name, readMethod, writeMethod);
    myRenderer = new BooleanRenderer();
    myEditor = new BooleanEditor();
  }

  public PropertyEditor<Boolean> getEditor(){
    return myEditor;
  }

  public void write(final Boolean value, final XmlWriter writer){
    writer.addAttribute("value", value.toString());
  }

  @NotNull
  public PropertyRenderer<Boolean> getRenderer(){
    return myRenderer;
  }
}
