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
package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.openapi.actionSystem.Anchor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ChangeTo extends ShowSuggestions implements SpellCheckerQuickFix {

  public ChangeTo(String wordWithTypo) {
    super(wordWithTypo);
  }


  @NotNull
  public String getName() {
    return SpellCheckerBundle.message("change.to");
  }

  @NotNull
  public String getFamilyName() {
    return SpellCheckerBundle.message("change.to");
  }

  @NotNull
  public Anchor getPopupActionAnchor() {
    return Anchor.FIRST;
  }


  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element == null) return;
    Editor editor = PsiUtilBase.findEditor(element);

    if (editor == null) {
      return;
    }

    TextRange textRange = ((ProblemDescriptorBase)descriptor).getTextRange();
    editor.getSelectionModel().setSelection(textRange.getStartOffset(), textRange.getEndOffset());

    String word = editor.getSelectionModel().getSelectedText();

    if (word == null || StringUtil.isEmpty(word)) {
      return;
    }

    List<LookupElement> lookupItems = new ArrayList<LookupElement>();
    for (String variant : getSuggestions(project)) {
      lookupItems.add(LookupElementBuilder.create(variant));
    }
    LookupElement[] items = new LookupElement[lookupItems.size()];
    items = lookupItems.toArray(items);
    LookupManager lookupManager = LookupManager.getInstance(project);
    lookupManager.showLookup(editor, items);

  }


}