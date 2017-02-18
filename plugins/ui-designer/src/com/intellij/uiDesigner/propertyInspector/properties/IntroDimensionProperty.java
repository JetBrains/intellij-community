/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntRegexEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.DimensionRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.snapShooter.SnapshotContext;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntroDimensionProperty extends IntrospectedProperty<Dimension> {
  private final Property[] myChildren;
  private final DimensionRenderer myRenderer;
  private final IntRegexEditor<Dimension> myEditor;

  public IntroDimensionProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient){
    super(name, readMethod, writeMethod, storeAsClient);
    myChildren = new Property[]{
      new IntFieldProperty(this, "width", -1, JBUI.emptySize()),
      new IntFieldProperty(this, "height", -1, JBUI.emptySize()),
    };
    myRenderer = new DimensionRenderer();
    myEditor = new IntRegexEditor<>(Dimension.class, myRenderer, new int[]{-1, -1});
  }

  @Override
  public void write(@NotNull final Dimension value, final XmlWriter writer) {
    writer.addAttribute("width", value.width);
    writer.addAttribute("height", value.height);
  }

  @NotNull
  public Property[] getChildren(final RadComponent component) {
    return myChildren;
  }

  @NotNull
  public PropertyRenderer<Dimension> getRenderer() {
    return myRenderer;
  }

  public PropertyEditor<Dimension> getEditor() {
    return myEditor;
  }

  @Override
  public void importSnapshotValue(final SnapshotContext context, final JComponent component, final RadComponent radComponent) {
    if (getName().equals(SwingProperties.MINIMUM_SIZE) ||
        getName().equals(SwingProperties.MAXIMUM_SIZE) ||
        getName().equals(SwingProperties.PREFERRED_SIZE)) {
      return;
    }
    super.importSnapshotValue(context, component, radComponent);
  }
}
