// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.core.SupportCode;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public class MissingMnemonicInspection extends BaseFormInspection {
  public MissingMnemonicInspection() {
    super("MissingMnemonic");
  }

  @Override
  protected void checkComponentProperties(Module module, @NotNull IComponent component, FormErrorCollector collector) {
    String value = FormInspectionUtil.getText(module, component);
    if (value == null) {
      return;
    }
    IProperty textProperty = FormInspectionUtil.findProperty(component, SwingProperties.TEXT);
    SupportCode.TextWithMnemonic twm = SupportCode.parseText(value);
    if (twm.myMnemonicIndex < 0 && !twm.myText.isEmpty()) {
      if (FormInspectionUtil.isComponentClass(module, component, AbstractButton.class)) {
        collector.addError(getID(), component, textProperty,
                           UIDesignerBundle.message("inspection.missing.mnemonics.message", value),
                           new MyEditorQuickFixProvider());
      }
      else if (FormInspectionUtil.isComponentClass(module, component, JLabel.class)) {
        IProperty labelForProperty = FormInspectionUtil.findProperty(component, SwingProperties.LABEL_FOR);
        if (labelForProperty != null && !StringUtil.isEmpty((String) labelForProperty.getPropertyValue(component))) {
          collector.addError(getID(), component, textProperty,
                             UIDesignerBundle.message("inspection.missing.mnemonics.message", value),
                             new MyEditorQuickFixProvider());
        }
      }
    }
  }

  private static class MyEditorQuickFixProvider implements EditorQuickFixProvider {
    @Override
    public @NotNull QuickFix createQuickFix(GuiEditor editor, @NotNull RadComponent component) {
      return new AssignMnemonicFix(editor, component,
                                   UIDesignerBundle.message("inspections.missing.mnemonic.quickfix"));
    }
  }
}
