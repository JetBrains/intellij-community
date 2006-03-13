package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.util.Comparing;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.lw.ColorDescriptor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.ColorEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.ColorRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.Color;
import java.lang.reflect.Method;

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
    if (colorDescriptor != null && colorDescriptor.isColorSet()) {
      super.setValueImpl(component, colorDescriptor.getResolvedColor());
    }
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    super.resetValue(component);
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), null);
  }

  @Override
  public void importSnapshotValue(final JComponent component, final RadComponent radComponent) {
    try {
      if (component.getParent() != null) {
        Color componentColor = (Color) myReadMethod.invoke(component, EMPTY_OBJECT_ARRAY);
        Color parentColor = (Color) myReadMethod.invoke(component.getParent(), EMPTY_OBJECT_ARRAY);
        if (componentColor != null && !Comparing.equal(componentColor, parentColor)) {
          setValue(radComponent, new ColorDescriptor(componentColor));
        }
      }
    }
    catch (Exception e) {
      // ignore
    }
  }
}
