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

import com.intellij.openapi.util.Comparing;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.lw.FontDescriptor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.FontEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.FontRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.snapShooter.SnapshotContext;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.Enumeration;

/**
 * @author yole
 */
public class IntroFontProperty extends IntrospectedProperty<FontDescriptor> {
  private final FontRenderer myFontRenderer = new FontRenderer();
  private FontEditor myFontEditor;
  @NonNls private static final String CLIENT_PROPERTY_KEY_PREFIX = "IntroFontProperty_";

  public IntroFontProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient) {
    super(name, readMethod, writeMethod, storeAsClient);
  }

  public void write(@NotNull FontDescriptor value, XmlWriter writer) {
    writer.writeFontDescriptor(value);
  }

  @NotNull public PropertyRenderer<FontDescriptor> getRenderer() {
    return myFontRenderer;
  }

  @Nullable public PropertyEditor<FontDescriptor> getEditor() {
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
  
  public static String descriptorToString(final FontDescriptor value) {
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
    if (result.length() > 0) {
      return result;
    }
    return UIDesignerBundle.message("font.default");
  }

  @Override public void importSnapshotValue(final SnapshotContext context, final JComponent component, final RadComponent radComponent) {
    try {
      if (component.getParent() != null) {
        Font componentFont = (Font) myReadMethod.invoke(component, EMPTY_OBJECT_ARRAY);
        if (componentFont instanceof FontUIResource) {
          final Constructor constructor = component.getClass().getConstructor(ArrayUtil.EMPTY_CLASS_ARRAY);
          constructor.setAccessible(true);
          JComponent newComponent = (JComponent)constructor.newInstance(ArrayUtil.EMPTY_OBJECT_ARRAY);
          Font defaultFont = (Font) myReadMethod.invoke(newComponent, EMPTY_OBJECT_ARRAY);

          if (defaultFont == componentFont) {
            return;
          }
          UIDefaults defaults = UIManager.getDefaults();
          Enumeration e = defaults.keys ();
          while(e.hasMoreElements()) {
            Object key = e.nextElement();
            Object value = defaults.get(key);
            if (key instanceof String && value == componentFont) {
              setValue(radComponent, FontDescriptor.fromSwingFont((String) key));
              return;
            }
          }
        }
        Font parentFont = (Font) myReadMethod.invoke(component.getParent(), EMPTY_OBJECT_ARRAY);
        if (!Comparing.equal(componentFont, parentFont)) {
          String fontName = componentFont.getName().equals(parentFont.getName()) ? null : componentFont.getName();
          int fontStyle = componentFont.getStyle() == parentFont.getStyle() ? -1 : componentFont.getStyle();
          int fontSize = componentFont.getSize() == parentFont.getSize() ? -1 : componentFont.getSize();
          setValue(radComponent, new FontDescriptor(fontName, fontStyle, fontSize));
        }
      }
    }
    catch (Exception e) {
      // ignore
    }
  }
}
