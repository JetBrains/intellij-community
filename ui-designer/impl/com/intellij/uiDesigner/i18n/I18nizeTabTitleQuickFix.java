package com.intellij.uiDesigner.i18n;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.RadTabbedPane;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.StringDescriptor;

/**
 * @author yole
 */
public class I18nizeTabTitleQuickFix extends I18nizeFormQuickFix {
  public I18nizeTabTitleQuickFix(final GuiEditor editor, final String name, final RadComponent component) {
    super(editor, name, component);
  }

  protected StringDescriptor getStringDescriptorValue() {
    RadTabbedPane tabbedPane = (RadTabbedPane) myComponent.getParent();
    return tabbedPane.getChildTitle(myComponent);
  }

  protected void setStringDescriptorValue(final StringDescriptor descriptor) throws Exception {
    RadTabbedPane tabbedPane = (RadTabbedPane) myComponent.getParent();
    tabbedPane.setChildTitle(myComponent, descriptor);
  }
}
