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
package com.intellij.lang.xml;

import com.intellij.codeInsight.unwrap.Unwrapper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import com.intellij.lang.ASTNode;

import java.util.Set;
import java.util.List;
import java.util.Collections;

public class XmlEnclosingTagUnwrapper implements Unwrapper {
  public boolean isApplicableTo(PsiElement e) {
    return true;
  }

  public void collectElementsToIgnore(PsiElement element, Set<PsiElement> result) {
  }

  public String getDescription(PsiElement e) {
    return XmlBundle.message("unwrap.enclosing.tag.name.action.name", ((XmlTag)e).getName());
  }

  public PsiElement collectAffectedElements(PsiElement e, List<PsiElement> toExtract) {
    return e;
  }

  public List<PsiElement> unwrap(Editor editor, PsiElement element) throws IncorrectOperationException {
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
