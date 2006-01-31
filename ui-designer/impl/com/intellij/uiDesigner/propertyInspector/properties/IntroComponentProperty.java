package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.ComponentEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.ComponentRenderer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * The value of the property is the string ID of the referenced component.
 * @author yole
 */
public class IntroComponentProperty extends IntrospectedProperty {
  private ComponentRenderer myRenderer = new ComponentRenderer();
  private ComponentEditor myEditor = new ComponentEditor();
  @NonNls private static final String CLIENT_PROPERTY_KEY_PREFIX = "IntroComponentProperty_";

  public IntroComponentProperty(final String name, final Method readMethod, final Method writeMethod) {
    super(name, readMethod, writeMethod);
  }

  @NotNull public PropertyRenderer getRenderer() {
    return myRenderer;
  }

  public PropertyEditor getEditor() {
    return myEditor;
  }

  public void write(@NotNull Object value, XmlWriter writer) {
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, (String) value);
  }

  @Override public Object getValue(final RadComponent component) {
    return component.getDelegee().getClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName());
  }

  @Override protected void setValueImpl(final RadComponent component, final Object value) throws Exception {
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), value);
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    setValue(component, null);
    markTopmostModified(component, false);
  }
}
