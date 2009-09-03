package com.intellij.uiDesigner.i18n;

import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.i18n.I18nizeFormQuickFix;
import com.intellij.uiDesigner.lw.StringDescriptor;

/**
 * @author yole
 */
public class I18nizeFormBorderQuickFix extends I18nizeFormQuickFix {
  private final RadContainer myContainer;

  public I18nizeFormBorderQuickFix(final GuiEditor editor, final String name, final RadContainer container) {
    super(editor, name, container);
    myContainer = container;
  }

  protected StringDescriptor getStringDescriptorValue() {
    return myContainer.getBorderTitle();
  }

  protected void setStringDescriptorValue(final StringDescriptor descriptor) throws Exception {
    myContainer.setBorderTitle(descriptor);
  }
}
