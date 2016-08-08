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
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.psi.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyStatementSelectionHandler extends ExtendWordSelectionHandlerBase {
  public boolean canSelect(final PsiElement e) {
    return e instanceof PyStringLiteralExpression || e instanceof PyCallExpression || e instanceof PyStatement ||
           e instanceof PyStatementList;
  }

  public List<TextRange> select(final PsiElement e, final CharSequence editorText, final int cursorOffset, final Editor editor) {
    List<TextRange> result = new ArrayList<>();
    PsiElement endElement = e;
    while(endElement.getLastChild() != null) {
      endElement = endElement.getLastChild();
    }
    if (endElement instanceof PsiWhiteSpace) {
      final PsiElement prevSibling = endElement.getPrevSibling();
      if (prevSibling != null) {
        endElement = prevSibling;
      }
    }
    result.addAll(expandToWholeLine(editorText, new TextRange(e.getTextRange().getStartOffset(),
                                                              endElement.getTextRange().getEndOffset())));

    return result;
  }
}
