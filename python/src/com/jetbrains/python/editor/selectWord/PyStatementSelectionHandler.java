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
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyStatementSelectionHandler extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@NotNull final PsiElement e) {
    return e instanceof PyStringLiteralExpression || e instanceof PyCallExpression || e instanceof PyStatement ||
           e instanceof PyStatementList;
  }

  @Override
  public List<TextRange> select(@NotNull final PsiElement e, @NotNull final CharSequence editorText, final int cursorOffset, @NotNull final Editor editor) {
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

    return new ArrayList<>(expandToWholeLine(editorText, new TextRange(e.getTextRange().getStartOffset(),
                                                                       endElement.getTextRange().getEndOffset())));
  }
}
