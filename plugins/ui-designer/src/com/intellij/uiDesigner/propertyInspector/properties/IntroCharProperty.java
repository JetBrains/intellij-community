package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.editors.CharEditor;

import java.lang.reflect.Method;

/**
 * @author yole
 */
public class IntroCharProperty extends IntroPrimitiveTypeProperty<Character> {
  public IntroCharProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient) {
    super(name, readMethod, writeMethod, storeAsClient, Character.class);
  }

  protected PropertyEditor<Character> createEditor() {
    return new CharEditor();
  }
}