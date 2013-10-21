/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyConditionalStatementPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyUtil;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   15.04.2010
 * Time:   19:33:14
 */
public class PyConditionalStatementPartFixer implements PyFixer {
  public void apply(Editor editor, PySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PyConditionalStatementPart) {
      final PyConditionalStatementPart conditionalStatementPart = (PyConditionalStatementPart)psiElement;
      final PyExpression condition = conditionalStatementPart.getCondition();
      final Document document = editor.getDocument();
      final PsiElement colon = PyUtil.getChildByFilter(conditionalStatementPart, TokenSet.create(PyTokenTypes.COLON), 0);
      if (colon == null) {
        if (condition != null) {
          final PsiElement firstNonComment = PyUtil.getFirstNonCommentAfter(condition.getNextSibling());
          if (firstNonComment != null && !":".equals(firstNonComment.getNode().getText())) {
            document.insertString(firstNonComment.getTextRange().getEndOffset(), ":");
          }
        }
        else {
          final PsiElement keywordToken = PyUtil.getChildByFilter(conditionalStatementPart,
                                                                  TokenSet.create(PyTokenTypes.IF_KEYWORD, PyTokenTypes.ELIF_KEYWORD,
                                                                                  PyTokenTypes.WHILE_KEYWORD), 0);
          final int offset = keywordToken.getTextRange().getEndOffset();
          document.insertString(offset, " :");
          processor.registerUnresolvedError(offset + 1);
        }
      } else if (condition == null) {
          processor.registerUnresolvedError(colon.getTextRange().getStartOffset());
      }
    }
  }
}
