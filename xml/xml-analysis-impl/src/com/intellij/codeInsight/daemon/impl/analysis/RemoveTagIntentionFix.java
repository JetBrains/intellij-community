/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoveTagIntentionFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final String myTagName;

  public RemoveTagIntentionFix(final String name, @NotNull final XmlTag tag) {
    super(tag);
    myTagName = name;
  }

  @NotNull
  @Override
  public String getText() {
    return XmlAnalysisBundle.message("xml.quickfix.remove.tag.text", myTagName);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return XmlAnalysisBundle.message("xml.quickfix.remove.tag.family");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final XmlTag next = editor != null ? PsiTreeUtil.getNextSiblingOfType(startElement, XmlTag.class) : null;
    final XmlTag prev = editor != null ? PsiTreeUtil.getPrevSiblingOfType(startElement, XmlTag.class) : null;

    startElement.delete();

    if (editor != null) {
      if (next != null) {
        editor.getCaretModel().moveToOffset(next.getTextRange().getStartOffset());
      }
      else if (prev != null) {
        editor.getCaretModel().moveToOffset(prev.getTextRange().getEndOffset());
      }
    }
  }
}
