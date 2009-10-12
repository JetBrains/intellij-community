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
package com.intellij.spellchecker.actions;


import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.spellchecker.SpellCheckerManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Completion action for spell checker.
 */
public final class CompleteWordFromDictionaryAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {


    Project project = e.getData(PlatformDataKeys.PROJECT);
    Editor editor = e.getData(PlatformDataKeys.EDITOR);
    PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);

    if (project != null && editor != null && psiFile != null) {
      // Get position before caret
      int caretOffset = editor.getCaretModel().getOffset();

      int documentOffset = caretOffset - 1;

      if (documentOffset > 0) {
        StringBuilder prefixBuilder = new StringBuilder();
        CharSequence charSequence = editor.getDocument().getCharsSequence();
        for (char c; documentOffset >= 0 && Character.isJavaIdentifierPart(c = charSequence.charAt(documentOffset)); documentOffset--) {
          prefixBuilder.append(c);
        }
        documentOffset = caretOffset;
        StringBuilder suffixBuilder = new StringBuilder();
        for (char c;
             documentOffset < charSequence.length() && Character.isJavaIdentifierPart(c = charSequence.charAt(documentOffset));
             documentOffset++) {
          suffixBuilder.append(c);
        }

        if (prefixBuilder.length() > 0) {
          String[] prefixes = NameUtil.nameToWords(prefixBuilder.reverse().toString());
          String prefix = prefixes.length > 0 ? prefixes[prefixes.length - 1] : "";
          if (prefix.length() > 0) {
            String[] suffixes = NameUtil.nameToWords(suffixBuilder.toString());
            String suffix = suffixes.length > 0 ? suffixes[0] : "";
            if (suffix.length() > 0 && Character.isLowerCase(suffix.charAt(0))) {
              // Select replacement part
              editor.getSelectionModel().setSelection(caretOffset, caretOffset + suffix.length());
            }
            List<String> variants = SpellCheckerManager.getInstance(project).getVariants(prefix);

            List<LookupElement> lookupItems = new ArrayList<LookupElement>();
            for (String variant : variants) {
              lookupItems.add(LookupElementBuilder.create(variant));
            }

            LookupElement[] items = lookupItems.toArray(new LookupElement[lookupItems.size()]);
            LookupManager.getInstance(project).showLookup(editor, items, prefix);
          }
        }
      }
    }
  }

  public void update(AnActionEvent e) {
    super.update(e);
    Project project = e.getData(LangDataKeys.PROJECT);
    Editor editor = e.getData(LangDataKeys.EDITOR);
    PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
    boolean available = project != null && editor != null && psiFile != null;
    Presentation presentation = e.getPresentation();
    if (presentation.isVisible()) {
      presentation.setVisible(available);
    }
    if (presentation.isEnabled()) {
      presentation.setEnabled(available);
    }
  }

  /* private static final class LookupItemPreferencePolicyImpl implements LookupItemPreferencePolicy {
      public void setPrefix(String prefix) {
      }

      public void itemSelected(LookupItem lookupItem) {
      }

      public int compare(LookupItem o1, LookupItem o2) {
          return o1.getLookupString().compareTo(o2.getLookupString());
      }
  }*/
}
