package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.editors.ColorEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.ColorRenderer;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.lw.ColorDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Method;
import java.awt.*;

/**
 * @author yole
 */
public class IntroColorProperty extends IntrospectedProperty {
  private ColorRenderer myColorRenderer = new ColorRenderer();
  private ColorEditor myColorEditor;
  @NonNls private static final String CLIENT_PROPERTY_KEY_PREFIX = "IntroColorProperty_";

  public IntroColorProperty(final String name, final Method readMethod, final Method writeMethod) {
    super(name, readMethod, writeMethod);
    myColorEditor = new ColorEditor(name);
  }

  @NotNull public PropertyRenderer getRenderer() {
    return myColorRenderer;
  }

  @Nullable public PropertyEditor getEditor() {
    return myColorEditor;
  }

  public void write(@NotNull Object value, XmlWriter writer) {
    ColorDescriptor colorDescriptor = (ColorDescriptor) value;
    Color color = colorDescriptor.getColor();
    if (color != null) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_COLOR, color.getRGB());
    }
    else if (colorDescriptor.getSwingColor() != null) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_SWING_COLOR, colorDescriptor.getSwingColor());
    }
    else if (colorDescriptor.getSystemColor() != null) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_SYSTEM_COLOR, colorDescriptor.getSystemColor());
    }
    else if (colorDescriptor.getAWTColor() != null) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_AWT_COLOR, colorDescriptor.getAWTColor());
    }
  }

  @Override public Object getValue(final RadComponent component) {
    final Object colorDescriptor = component.getDelegee().getClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName());
    if (colorDescriptor == null) {
      return new ColorDescriptor((Color) super.getValue(component));
    }
    return colorDescriptor;
  }

  @Override protected void setValueImpl(final RadComponent component, final Object value) throws Exception {
    ColorDescriptor colorDescriptor = (ColorDescriptor) value;
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), colorDescriptor);
    if (colorDescriptor != null) {
      super.setValueImpl(component, colorDescriptor.getResolvedColor());
    }
  }
}
