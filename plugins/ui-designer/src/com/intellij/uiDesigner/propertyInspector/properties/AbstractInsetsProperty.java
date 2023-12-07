// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntRegexEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.InsetsPropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public abstract class AbstractInsetsProperty<T extends RadComponent> extends Property<T, Insets> {
  private final Property[] myChildren;
  private final InsetsPropertyRenderer myRenderer;
  private IntRegexEditor<Insets> myEditor;

  public AbstractInsetsProperty(final @NonNls String name) {
    this(null, name);
  }

  public AbstractInsetsProperty(Property parent, final @NonNls String name){
    super(parent, name);
    myChildren=new Property[]{
      new IntFieldProperty(this, "top", 0, new Insets(0, 0, 0, 0)),
      new IntFieldProperty(this, "left", 0, new Insets(0, 0, 0, 0)),
      new IntFieldProperty(this, "bottom", 0, new Insets(0, 0, 0, 0)),
      new IntFieldProperty(this, "right", 0, new Insets(0, 0, 0, 0)),
    };
    myRenderer=new InsetsPropertyRenderer();
  }

  @Override
  public final Property @NotNull [] getChildren(final RadComponent component) {
    return myChildren;
  }

  @Override
  public final @NotNull PropertyRenderer<Insets> getRenderer() {
    return myRenderer;
  }

  @Override
  public final PropertyEditor<Insets> getEditor() {
    if (myEditor == null) {
      myEditor = new IntRegexEditor<>(Insets.class, myRenderer, new int[]{0, 0, 0, 0}) {
        @Override
        public Insets getValue() throws Exception {
          // if a single number has been entered, interpret it as same value for all parts (IDEADEV-7330)
          try {
            int value = Integer.parseInt(myTf.getText());
            final Insets insets = new Insets(value, value, value, value);
            myTf.setText(myRenderer.formatText(insets));
            return insets;
          }
          catch (NumberFormatException ex) {
            return super.getValue();
          }
        }
      };
    }
    return myEditor;
  }
}
