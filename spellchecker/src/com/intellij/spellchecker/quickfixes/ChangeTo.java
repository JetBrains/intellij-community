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
import com.intellij.spellchecker.util.SpellCheckerBundle;
import org.jetbrains.annotations.NotNull;


public class ChangeTo extends ShowSuggestions implements SpellCheckerQuickFix {

  public static final String FIX_NAME = SpellCheckerBundle.message("change.to");

  public ChangeTo(String wordWithTypo) {
    super(wordWithTypo);
  }

  @NotNull
  public String getFamilyName() {
    return FIX_NAME;
  }

  @NotNull
  public Anchor getPopupActionAnchor() {
    return Anchor.FIRST;
  }


  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (element == null) return;
    final Editor editor = getEditor(element, project);
    if (editor == null) return;

    final TextRange textRange = ((ProblemDescriptorBase)descriptor).getTextRange();
    final int documentLength = editor.getDocument().getTextLength();
    final int textEndOffset = textRange.getEndOffset();
    final int endOffset = textEndOffset <= documentLength ? textEndOffset : documentLength;
    editor.getSelectionModel().setSelection(textRange.getStartOffset(), endOffset);
    final String word = editor.getSelectionModel().getSelectedText();

    if (word == null || StringUtil.isEmpty(word)) {
      return;
    }
    final LookupElement[] items = getSuggestions(project)
      .stream()
      .map(LookupElementBuilder::create)
      .toArray(LookupElement[]::new);
    LookupManager.getInstance(project).showLookup(editor, items);
  }
}