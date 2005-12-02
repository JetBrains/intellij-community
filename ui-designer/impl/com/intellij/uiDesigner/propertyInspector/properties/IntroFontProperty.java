package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.lw.FontDescriptor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.FontEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.reflect.Method;

/**
 * @author yole
 */
public class IntroFontProperty extends IntrospectedProperty {
  private MyFontRenderer myFontRenderer = new MyFontRenderer();
  private FontEditor myFontEditor;
  @NonNls private static final String CLIENT_PROPERTY_KEY_PREFIX = "IntroFontProperty_";

  public IntroFontProperty(final String name, final Method readMethod, final Method writeMethod) {
    super(name, readMethod, writeMethod);
    myFontEditor = new FontEditor(name);
  }

  public void write(@NotNull Object value, XmlWriter writer) {
    FontDescriptor descriptor = (FontDescriptor) value;
    Font font = descriptor.getFont();
    if (font != null) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_NAME, font.getName());
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_SIZE, font.getSize());
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_STYLE, font.getStyle());
    }
    else if (descriptor.getSwingFont() != null) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_SWING_FONT, descriptor.getSwingFont());
    }
  }

  @NotNull public Property[] getChildren() {
    return EMPTY_ARRAY;
  }

  @NotNull public PropertyRenderer getRenderer() {
    return myFontRenderer;
  }

  @Nullable public PropertyEditor getEditor() {
    return myFontEditor;
  }

  @Override public Object getValue(final RadComponent component) {
    final Object fontDescriptor = component.getDelegee().getClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName());
    if (fontDescriptor == null) {
      return new FontDescriptor((Font) super.getValue(component));
    }
    return fontDescriptor;
  }

  @Override protected void setValueImpl(final RadComponent component, final Object value) throws Exception {
    FontDescriptor fontDescriptor = (FontDescriptor) value;
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), fontDescriptor);
    if (fontDescriptor != null) {
      super.setValueImpl(component, fontDescriptor.getResolvedFont());
    }
  }

  public static String descriptorToString(final FontDescriptor value) {
    Font font = value.getFont();
    if (font != null) {
      return fontToString(font);
    }
    if (value.getSwingFont() != null) {
      return value.getSwingFont();
    }
    throw new IllegalStateException("Unknown font type");
  }

  public static String fontToString(final Font font) {
    StringBuilder result = new StringBuilder(font.getFamily());
    result.append(" ").append(font.getSize());
    if ((font.getStyle() & Font.BOLD) != 0) {
      result.append(" ").append(UIDesignerBundle.message("font.chooser.bold"));
    }
    if ((font.getStyle() & Font.ITALIC) != 0) {
      result.append(" ").append(UIDesignerBundle.message("font.chooser.italic"));
    }
    return result.toString();
  }

  private static class MyFontRenderer extends LabelPropertyRenderer {
    protected void customize(Object value) {
      FontDescriptor fontDescriptor = (FontDescriptor) value;
      setText(descriptorToString(fontDescriptor));
    }
  }
}
