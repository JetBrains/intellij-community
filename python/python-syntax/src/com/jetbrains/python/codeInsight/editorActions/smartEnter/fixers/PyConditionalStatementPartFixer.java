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
package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstConditionalStatementPart;
import com.jetbrains.python.ast.PyAstExpression;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PyConditionalStatementPartFixer extends PyFixer<PyAstConditionalStatementPart> {
  public PyConditionalStatementPartFixer() {
    super(PyAstConditionalStatementPart.class);
  }

  @Override
  public void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyAstConditionalStatementPart statementPart)
    throws IncorrectOperationException {
    final PyAstExpression condition = statementPart.getCondition();
    final Document document = editor.getDocument();
    final PsiElement colon = PyPsiUtilsCore.getFirstChildOfType(statementPart, PyTokenTypes.COLON);
    if (colon == null) {
      if (condition != null) {
        final PsiElement firstNonComment = PyPsiUtilsCore.getNextNonCommentSibling(condition.getNextSibling(), false);
        if (firstNonComment != null && !":".equals(firstNonComment.getNode().getText())) {
          document.insertString(firstNonComment.getTextRange().getEndOffset(), ":");
        }
      }
      else {
        final TokenSet keywords = TokenSet.create(PyTokenTypes.IF_KEYWORD, PyTokenTypes.ELIF_KEYWORD, PyTokenTypes.WHILE_KEYWORD);
        final PsiElement keywordToken = PyPsiUtilsCore.getChildByFilter(statementPart,
                                                                    keywords, 0);
        final int offset = Objects.requireNonNull(keywordToken).getTextRange().getEndOffset();
        document.insertString(offset, " :");
        processor.registerUnresolvedError(offset + 1);
      }
    }
    else if (condition == null) {
      processor.registerUnresolvedError(colon.getTextRange().getStartOffset());
    }
  }
}
