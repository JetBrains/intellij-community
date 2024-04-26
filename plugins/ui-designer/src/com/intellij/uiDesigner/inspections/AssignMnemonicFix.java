// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.inspections;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.StringDescriptorManager;
import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.core.SupportCode;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.properties.IntroStringProperty;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.util.ArrayUtilRt;

import java.util.ArrayList;


public class AssignMnemonicFix extends QuickFix {
  public AssignMnemonicFix(final GuiEditor editor, final RadComponent component, final @IntentionName String name) {
    super(editor, name, component);
  }

  @Override
  public void run() {
    IProperty textProperty = FormInspectionUtil.findProperty(myComponent, SwingProperties.TEXT);
    StringDescriptor descriptor = (StringDescriptor) textProperty.getPropertyValue(myComponent);
    String value = StringDescriptorManager.getInstance(myComponent.getModule()).resolve(myComponent, descriptor);
    String[] variants = fillMnemonicVariants(SupportCode.parseText(value).myText);
    String result = Messages.showEditableChooseDialog(UIDesignerBundle.message("inspection.missing.mnemonics.quickfix.prompt"),
                                                      UIDesignerBundle.message("inspection.missing.mnemonics.quickfix.title"),
                                                      Messages.getQuestionIcon(), variants, variants [0], null);
    if (result != null) {
      if (!myEditor.ensureEditable()) {
        return;
      }
      FormInspectionUtil.updateStringPropertyValue(myEditor, myComponent, (IntroStringProperty)textProperty, descriptor, result);
    }
  }

  private String[] fillMnemonicVariants(final String value) {
    final StringBuffer usedMnemonics = new StringBuffer();
    RadContainer container = myComponent.getParent();
    if (container != null) {
      while (container.getParent() != null) {
        container = container.getParent();
      }
      FormEditingUtil.iterate(container, new FormEditingUtil.ComponentVisitor() {
        @Override
        public boolean visit(final IComponent component) {
          SupportCode.TextWithMnemonic twm = DuplicateMnemonicInspection.getTextWithMnemonic(myEditor.getModule(), component);
          if (twm != null) {
            usedMnemonics.append(twm.getMnemonicChar());
          }
          return true;
        }
      });
    }

    ArrayList<String> variants = new ArrayList<>();
    // try upper-case and word start characters
    for(int i=0; i<value.length(); i++) {
      final char ch = value.charAt(i);
      if (i == 0 || Character.isUpperCase(ch) || value.charAt(i - 1) == ' ') {
        if (Character.isLetter(ch) && usedMnemonics.indexOf(StringUtil.toUpperCase(String.valueOf(ch))) < 0) {
          variants.add(value.substring(0, i) + "&" + value.substring(i));
        }
      }
    }

    if (variants.isEmpty()) {
      // try any unused characters
      for(int i=0; i<value.length(); i++) {
        final char ch = value.charAt(i);
        if (Character.isLetter(ch) && usedMnemonics.indexOf(StringUtil.toUpperCase(String.valueOf(ch))) < 0) {
          variants.add(value.substring(0, i) + "&" + value.substring(i));
        }
      }
    }

    if (variants.isEmpty()) {
      variants.add(value);
    }
    return ArrayUtilRt.toStringArray(variants);
  }
}
