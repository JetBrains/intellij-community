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

import com.intellij.openapi.ui.Messages;
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
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;

/**
 * @author yole
 */
public class AssignMnemonicFix extends QuickFix {
  public AssignMnemonicFix(final GuiEditor editor, final RadComponent component, final String name) {
    super(editor, name, component);
  }

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
      if (i == 0 || Character.isUpperCase(ch) || (i > 0 && value.charAt(i-1) == ' ')) {
        if (Character.isLetter(ch) && usedMnemonics.indexOf(String.valueOf(ch).toUpperCase()) < 0) {
          variants.add(value.substring(0, i) + "&" + value.substring(i));
        }
      }
    }

    if (variants.size() == 0) {
      // try any unused characters
      for(int i=0; i<value.length(); i++) {
        final char ch = value.charAt(i);
        if (Character.isLetter(ch) && usedMnemonics.indexOf(String.valueOf(ch).toUpperCase()) < 0) {
          variants.add(value.substring(0, i) + "&" + value.substring(i));
        }
      }
    }

    if (variants.size() == 0) {
      variants.add(value);
    }
    return ArrayUtil.toStringArray(variants);
  }
}
