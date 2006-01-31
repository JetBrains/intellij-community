package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.ReferenceUtil;
import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.core.SupportCode;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.quickFixes.QuickFix;

import javax.swing.*;

/**
 * @author yole
 */
public class MissingMnemonicInspection extends BaseFormInspection {
  public MissingMnemonicInspection() {
    super("MissingMnemonic");
  }

  @Override public String getDisplayName() {
    return UIDesignerBundle.message("inspection.missing.mnemonics");
  }

  protected void checkComponentProperties(Module module, IComponent component, FormErrorCollector collector) {
    IProperty textProperty = DuplicateMnemonicInspection.findProperty(component, SwingProperties.TEXT);
    if (textProperty != null) {
      Object propValue = textProperty.getPropertyValue(component);
      if (propValue instanceof StringDescriptor) {
        StringDescriptor descriptor = (StringDescriptor) propValue;
        String value;
        if (component instanceof RadComponent) {
          value = ReferenceUtil.resolve((RadComponent) component, descriptor);
        }
        else {
          value = ReferenceUtil.resolve(module, descriptor, null);
        }
        SupportCode.TextWithMnemonic twm = SupportCode.parseText(value);
        if (twm.myMnemonicIndex < 0) {
          if (FormInspectionUtil.isComponentClass(module, component, JButton.class)) {
            collector.addError(textProperty,
                               UIDesignerBundle.message("inspection.missing.mnemonics.message", value),
                               new MyEditorQuickFixProvider());
          }
          else if (FormInspectionUtil.isComponentClass(module, component, JLabel.class)) {
            IProperty labelForProperty = DuplicateMnemonicInspection.findProperty(component, SwingProperties.LABEL_FOR);
            if (labelForProperty != null && !StringUtil.isEmpty((String) labelForProperty.getPropertyValue(component))) {
              collector.addError(textProperty,
                                 UIDesignerBundle.message("inspection.missing.mnemonics.message", value),
                                 new MyEditorQuickFixProvider());
            }
          }
        }
      }
    }
  }

  private static class MyEditorQuickFixProvider implements EditorQuickFixProvider {
    public QuickFix createQuickFix(GuiEditor editor, RadComponent component) {
      return new AssignMnemonicFix(editor, component,
                                   UIDesignerBundle.message("inspections.missing.mnemonic.quickfix"));
    }
  }
}
