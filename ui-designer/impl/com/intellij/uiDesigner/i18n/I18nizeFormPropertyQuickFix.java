package com.intellij.uiDesigner.i18n;

import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.i18n.I18nizeFormQuickFix;
import com.intellij.uiDesigner.lw.StringDescriptor;

/**
 * @author yole
 */
public class I18nizeFormPropertyQuickFix extends I18nizeFormQuickFix {
  private final IntrospectedProperty myProperty;

  public I18nizeFormPropertyQuickFix(final GuiEditor editor, final String name, final RadComponent component,
                                     final IntrospectedProperty property) {
    super(editor, name, component);
    myProperty = property;
  }

  protected StringDescriptor getStringDescriptorValue() {
    return (StringDescriptor) myProperty.getValue(myComponent);
  }

  protected void setStringDescriptorValue(final StringDescriptor descriptor) throws Exception {
    myProperty.setValue(myComponent, descriptor);
  }
}
