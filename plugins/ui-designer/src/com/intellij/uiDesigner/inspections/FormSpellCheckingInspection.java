// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.TextRange;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.properties.IntroStringProperty;
import com.intellij.uiDesigner.quickFixes.PopupQuickFix;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class FormSpellCheckingInspection extends StringDescriptorInspection {
  public FormSpellCheckingInspection() {
    super("FormSpellChecking");
  }

  @Override
  public @Nullable String getAlternativeID() {
    return "SpellCheckingInspection";
  }

  @Override
  protected void checkStringDescriptor(Module module,
                                       final IComponent component,
                                       final IProperty prop,
                                       final StringDescriptor descriptor,
                                       final FormErrorCollector collector) {
    final String value = descriptor.getResolvedValue();
    if (value == null) {
      return;
    }
    final SpellCheckerManager manager = SpellCheckerManager.getInstance(module.getProject());
    PlainTextSplitter.getInstance().split(value, TextRange.allOf(value), textRange -> {
      final String word = textRange.substring(value);
      if (manager.hasProblem(word)) {
        final List<String> suggestions = manager.getSuggestions(word);
        if (!suggestions.isEmpty() && prop instanceof IntroStringProperty) {
          EditorQuickFixProvider changeToProvider =
            (editor, component1) -> new PopupQuickFix<String>(editor, UIDesignerBundle.message("inspection.editor.quick.fix.name"),
                                                              component1) {
              @Override
              public void run() {
                ListPopup popup = JBPopupFactory.getInstance().createListPopup(getPopupStep());
                popup.showUnderneathOf(component1.getDelegee());
              }

              @Override
              public ListPopupStep<String> getPopupStep() {
                return new BaseListPopupStep<>(UIDesignerBundle.message("popup.title.select.replacement"), suggestions) {
                  @Override
                  public PopupStep onChosen(String selectedValue, boolean finalChoice) {
                    FormInspectionUtil.updateStringPropertyValue(editor, component1, (IntroStringProperty)prop, descriptor, selectedValue);
                    return FINAL_CHOICE;
                  }
                };
              }
            };
          EditorQuickFixProvider acceptProvider =
            (editor, component1) -> new QuickFix(editor, UIDesignerBundle.message("intention.name.save.to.dictionary", word), component1) {
              @Override
              public void run() {
                manager.acceptWordAsCorrect(word, editor.getProject());
              }
            };
          collector.addError(getID(), component, prop, UIDesignerBundle.message("inspection.message.typo.in.word", word), changeToProvider, acceptProvider);
        }
        else {
          collector.addError(getID(), component, prop, UIDesignerBundle.message("inspection.message.typo.in.word", word));
        }
      }
    });
  }
}
