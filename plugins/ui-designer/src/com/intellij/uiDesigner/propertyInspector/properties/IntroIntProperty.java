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

import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.snapShooter.SnapshotContext;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.SwingProperties;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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
      myRenderer = new LabelPropertyRenderer<>();
    }
    return myRenderer;
  }

  public PropertyEditor<Integer> getEditor() {
    if (myEditor == null) {
      myEditor = new IntEditor(Integer.MIN_VALUE);
    }
    return myEditor;
  }

  @Override
  public void importSnapshotValue(final SnapshotContext context, final JComponent component, final RadComponent radComponent) {
    // exclude property from snapshot import to avoid exceptions because of not imported model 
    if (!getName().equals(SwingProperties.SELECTED_INDEX)) {
      super.importSnapshotValue(context, component, radComponent);
    }
  }
}
