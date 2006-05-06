package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntEditor;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntroIntProperty extends IntrospectedProperty<Integer> {
  private PropertyRenderer<Integer> myRenderer;
  private PropertyEditor<Integer> myEditor;

  public IntroIntProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient) {
    this(name, readMethod, writeMethod, null, null, storeAsClient);
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
  public PropertyRenderer<Integer> getRenderer() {
    if (myRenderer == null) {
      myRenderer = new LabelPropertyRenderer<Integer>();
    }
    return myRenderer;
  }

  public PropertyEditor<Integer> getEditor() {
    if (myEditor == null) {
      myEditor = new IntEditor(Integer.MIN_VALUE);
    }
    return myEditor;
  }

  public void write(final Integer value, final XmlWriter writer){
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, value.intValue());
  }
}
