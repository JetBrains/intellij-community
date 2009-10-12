/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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