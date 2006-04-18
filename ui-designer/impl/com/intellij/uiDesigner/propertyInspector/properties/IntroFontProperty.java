package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.lw.FontDescriptor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.FontEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.Font;
import java.lang.reflect.Method;

/**
 * @author yole
 */
public class IntroFontProperty extends IntrospectedProperty<FontDescriptor> {
  private MyFontRenderer myFontRenderer = new MyFontRenderer();
  private FontEditor myFontEditor;
  @NonNls private static final String CLIENT_PROPERTY_KEY_PREFIX = "IntroFontProperty_";

  public IntroFontProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient) {
    super(name, readMethod, writeMethod, storeAsClient);
  }

  public void write(@NotNull FontDescriptor value, XmlWriter writer) {
    Font font = value.getFont();
    if (font != null) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_NAME, font.getName());
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_SIZE, font.getSize());
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_STYLE, font.getStyle());
    }
    else if (value.getSwingFont() != null) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_SWING_FONT, value.getSwingFont());
    }
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
      return new FontDescriptor((Font) invokeGetter(component));
    }
    return fontDescriptor;
  }

  @Override protected void setValueImpl(final RadComponent component, final FontDescriptor value) throws Exception {
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), value);
    if (value != null) {
      invokeSetter(component, value.getResolvedFont());
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

  private static class MyFontRenderer extends LabelPropertyRenderer<FontDescriptor> {
    protected void customize(FontDescriptor value) {
      setText(descriptorToString(value));
    }
  }

  @Override public void importSnapshotValue(final JComponent component, final RadComponent radComponent) {
    try {
      if (component.getParent() != null) {
        Font componentFont = (Font) myReadMethod.invoke(component, EMPTY_OBJECT_ARRAY);
        Font parentFont = (Font) myReadMethod.invoke(component.getParent(), EMPTY_OBJECT_ARRAY);
        if (!Comparing.equal(componentFont, parentFont)) {
          setValue(radComponent, new FontDescriptor(componentFont));
        }
      }
    }
    catch (Exception e) {
      // ignore
    }
  }
}
