// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.lw.FontDescriptor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.FontEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.FontRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.reflect.Method;


public class IntroFontProperty extends IntrospectedProperty<FontDescriptor> {
  private final FontRenderer myFontRenderer = new FontRenderer();
  private FontEditor myFontEditor;
  private static final @NonNls String CLIENT_PROPERTY_KEY_PREFIX = "IntroFontProperty_";

  public IntroFontProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient) {
    super(name, readMethod, writeMethod, storeAsClient);
  }

  @Override
  public void write(@NotNull FontDescriptor value, XmlWriter writer) {
    writer.writeFontDescriptor(value);
  }

  @Override
  public @NotNull PropertyRenderer<FontDescriptor> getRenderer() {
    return myFontRenderer;
  }

  @Override
  public @Nullable PropertyEditor<FontDescriptor> getEditor() {
    if (myFontEditor == null) {
      myFontEditor = new FontEditor(getName());
    }
    return myFontEditor;
  }

  @Override public FontDescriptor getValue(final RadComponent component) {
    final FontDescriptor fontDescriptor = (FontDescriptor) component.getDelegee().getClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName());
    if (fontDescriptor == null) {
      return new FontDescriptor(null, -1, -1);
    }
    return fontDescriptor;
  }

  @Override protected void setValueImpl(final RadComponent component, final FontDescriptor value) throws Exception {
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), value);
    if (value != null) {
      if (!component.isLoadingProperties()) {
        invokeSetter(component, getDefaultValue(component.getDelegee()));
      }
      Font defaultFont = (Font) invokeGetter(component);
      final Font resolvedFont = value.getResolvedFont(defaultFont);
      invokeSetter(component, resolvedFont);
    }
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    super.resetValue(component);
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), null);
  }

  public static @NlsSafe String descriptorToString(final FontDescriptor value) {
    if (value == null) {
      return "";
    }
    if (value.getSwingFont() != null) {
      return value.getSwingFont();
    }
    StringBuilder builder = new StringBuilder();
    if (value.getFontName() != null) {
      builder.append(value.getFontName());
    }
    if (value.getFontSize() >= 0) {
      builder.append(' ').append(value.getFontSize()).append(" pt");
    }
    if (value.getFontStyle() >= 0) {
      if (value.getFontStyle() == 0) {
        builder.append(' ').append(UIDesignerBundle.message("font.chooser.regular"));
      }
      else {
        if ((value.getFontStyle() & Font.BOLD) != 0) {
          builder.append(' ').append(UIDesignerBundle.message("font.chooser.bold"));
        }
        if ((value.getFontStyle() & Font.ITALIC) != 0) {
          builder.append(" ").append(UIDesignerBundle.message("font.chooser.italic"));
        }

      }
    }
    String result = builder.toString().trim();
    if (!result.isEmpty()) {
      return result;
    }
    return UIDesignerBundle.message("font.default");
  }
}
