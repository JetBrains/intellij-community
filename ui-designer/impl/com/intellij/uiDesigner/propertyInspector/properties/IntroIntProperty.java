package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.IntRenderer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntroIntProperty extends IntrospectedProperty<Integer> {
  private final PropertyRenderer<Integer> myRenderer;
  private final PropertyEditor<Integer> myEditor;

  public IntroIntProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient) {
    this(name, readMethod, writeMethod, new IntRenderer(), new IntEditor(Integer.MIN_VALUE), storeAsClient);
  }

  public IntroIntProperty(final String name,
                          final Method readMethod,
                          final Method writeMethod,
                          final PropertyRenderer<Integer> renderer,
                          final PropertyEditor<Integer> editor,
                          final boolean storeAsClient){
    super(name, readMethod, writeMethod, storeAsClient);
    myRenderer = renderer;
    myEditor = editor;
  }

  @NotNull
  public PropertyRenderer<Integer> getRenderer(){
    return myRenderer;
  }

  public PropertyEditor<Integer> getEditor(){
    return myEditor;
  }

  public void write(final Integer value, final XmlWriter writer){
    writer.addAttribute("value", value.intValue());
  }
}
