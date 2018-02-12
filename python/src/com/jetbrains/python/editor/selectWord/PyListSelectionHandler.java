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
package com.jetbrains.python.editor.selectWord;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyParameterList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * User: catherine
 *
 * Handler to select list contents without parentheses 
 */
public class PyListSelectionHandler extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PyListLiteralExpression || e instanceof PyParameterList || e instanceof PyArgumentList;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    TextRange stringRange = e.getTextRange();
    PsiElement firstChild = e.getFirstChild().getNextSibling();
    if (firstChild instanceof PsiErrorElement) {
      return Collections.emptyList();
    }
    int startShift = 1;
    if (firstChild instanceof PsiWhiteSpace)
      startShift += firstChild.getTextLength();
    PsiElement lastChild = e.getLastChild().getPrevSibling();
    int endShift = 1;
    if (lastChild instanceof PsiWhiteSpace && lastChild != firstChild)
      endShift += lastChild.getTextLength();

    final TextRange offsetRange = new TextRange(stringRange.getStartOffset() + startShift, stringRange.getEndOffset() - endShift);
    if (offsetRange.contains(cursorOffset) && offsetRange.getLength() > 1) {
      return Collections.singletonList(offsetRange);
    }
    return Collections.emptyList();
  }
}
