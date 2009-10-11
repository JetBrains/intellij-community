/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author yole
 */
public class MissingMnemonicInspection extends BaseFormInspection {
  public MissingMnemonicInspection() {
    super("MissingMnemonic");
  }

  @NotNull
  @Override public String getDisplayName() {
    return UIDesignerBundle.message("inspection.missing.mnemonics");
  }

  protected void checkComponentProperties(Module module, IComponent component, FormErrorCollector collector) {
    String value = FormInspectionUtil.getText(module, component);
    if (value == null) {
      return;
    }
    IProperty textProperty = FormInspectionUtil.findProperty(component, SwingProperties.TEXT);
    SupportCode.TextWithMnemonic twm = SupportCode.parseText(value);
    if (twm.myMnemonicIndex < 0 && twm.myText.length() > 0) {
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
    public QuickFix createQuickFix(GuiEditor editor, RadComponent component) {
      return new AssignMnemonicFix(editor, component,
                                   UIDesignerBundle.message("inspections.missing.mnemonic.quickfix"));
    }
  }
}
