// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  protected PropertyEditor<Character> createEditor() {
    return new CharEditor();
  }
}