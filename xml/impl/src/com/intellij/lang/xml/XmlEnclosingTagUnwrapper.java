/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.xml;

import com.intellij.codeInsight.unwrap.Unwrapper;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class XmlEnclosingTagUnwrapper implements Unwrapper {
  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    return true;
  }

  @Override
  public void collectElementsToIgnore(@NotNull PsiElement element, @NotNull Set<PsiElement> result) {
  }

  @NotNull
  @Override
  public String getDescription(@NotNull PsiElement e) {
    return XmlBundle.message("unwrap.enclosing.tag.name.action.name", ((XmlTag)e).getName());
  }

  @Override
  public PsiElement collectAffectedElements(@NotNull PsiElement element, @NotNull List<PsiElement> toExtract) {
    final TextRange range = element.getTextRange();
    final ASTNode startTagNameEnd = XmlChildRole.START_TAG_END_FINDER.findChild(element.getNode());
    final ASTNode endTagNameStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(element.getNode());

    int start = startTagNameEnd != null ? startTagNameEnd.getTextRange().getEndOffset() : range.getStartOffset();
    int end = endTagNameStart != null ? endTagNameStart.getTextRange().getStartOffset() : range.getEndOffset();

    for (PsiElement child : element.getChildren()) {
      final TextRange childRange = child.getTextRange();
      if (childRange.getStartOffset() >= start && childRange.getEndOffset() <= end) {
        toExtract.add(child);
      }
    }
    return element;
  }

  @NotNull
  @Override
  public List<PsiElement> unwrap(@NotNull Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final TextRange range = element.getTextRange();
    final ASTNode startTagNameEnd = XmlChildRole.START_TAG_END_FINDER.findChild(element.getNode());
    final ASTNode endTagNameStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(element.getNode());

    if (endTagNameStart != null) {
      editor.getDocument().replaceString(endTagNameStart.getTextRange().getStartOffset(), range.getEndOffset(), "");
      editor.getDocument().replaceString(range.getStartOffset(), startTagNameEnd.getTextRange().getEndOffset(), "");
    }
    else {
      editor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), "");
    }
    return Collections.emptyList();
  }
}
