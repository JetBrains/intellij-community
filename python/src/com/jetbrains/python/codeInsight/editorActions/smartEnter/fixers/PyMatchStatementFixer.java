// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.psi.PyUtil.as;

public final class PyMatchStatementFixer extends PyFixer<PyStatement> {
  public PyMatchStatementFixer() {
    super(PyStatement.class);
  }

  @Override
  protected void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyStatement element) {
    Document document = editor.getDocument();
    PyMatchStatement matchStatement = as(element, PyMatchStatement.class);
    if (matchStatement != null) {
      PsiElement colon = PyPsiUtils.getFirstChildOfType(element, PyTokenTypes.COLON);
      assert colon != null;
      int colonEndOffset = colon.getTextRange().getEndOffset();
      // It's not enough to check matchStatement.getCaseClauses().isEmpty()
      boolean hasEmptyBody = colonEndOffset == matchStatement.getTextRange().getEndOffset();
      if (hasEmptyBody) {
        String matchIndent = PyIndentUtil.getElementIndent(element);
        String indent = PyIndentUtil.getIndentFromSettings(element.getContainingFile());
        String caseClausePlaceholder = "\n" + matchIndent + indent + PyNames.CASE + " :";
        document.insertString(colonEndOffset, caseClausePlaceholder);
        processor.registerUnresolvedError(colonEndOffset + caseClausePlaceholder.length() - 1);
      }
      return;
    }

    PyTypeDeclarationStatement typeDeclaration = as(element, PyTypeDeclarationStatement.class);
    // "match:" case
    if (typeDeclaration != null && isMatchIdentifier(typeDeclaration.getTarget())) {
      PyAnnotation annotation = typeDeclaration.getAnnotation();
      if (annotation != null && annotation.getValue() == null) {
        processor.registerUnresolvedError(annotation.getTextRange().getStartOffset());
        return;
      }
    }

    Couple<PsiElement> pair = findMatchKeywordAndSubjectInExpressionStatement(element);
    PsiElement matchKeyword = pair.getFirst();
    if (matchKeyword == null) {
      return;
    }
    PsiElement subject = pair.getSecond();
    if (subject != null) {
      int endOffset = subject.getTextRange().getEndOffset();
      String matchIndent = PyIndentUtil.getElementIndent(matchKeyword);
      String indent = PyIndentUtil.getIndentFromSettings(element.getContainingFile());
      String caseClausePlaceholder = ":\n" + matchIndent + indent + PyNames.CASE + " :";
      document.insertString(endOffset, caseClausePlaceholder);
      processor.registerUnresolvedError(endOffset + caseClausePlaceholder.length() - 1);
    }
    else {
      int endOffset = element.getTextRange().getEndOffset();
      document.insertString(endOffset, " :");
      processor.registerUnresolvedError(endOffset + 1);
    }
  }

  @NotNull
  private static Couple<PsiElement> findMatchKeywordAndSubjectInExpressionStatement(@NotNull PyStatement statement) {
    if (!(statement instanceof PyExpressionStatement)) return Couple.getEmpty();
    // "match <caret>expr" case
    PsiElement prevSibling = PyPsiUtils.getPrevNonWhitespaceSiblingOnSameLine(statement);
    if (prevSibling instanceof PyExpressionStatement && isMatchIdentifier(prevSibling)) {
      return Couple.of(prevSibling, statement);
    }
    if (isMatchIdentifier(statement)) {
      // "<caret>match expr"
      PsiElement nextSibling = PyPsiUtils.getNextNonWhitespaceSiblingOnSameLine(statement);
      if (nextSibling instanceof PyExpressionStatement) {
        return Couple.of(statement, nextSibling);
      }
      // "match" case
      else if (nextSibling == null) {
        return Couple.of(statement, null);
      }
    }
    // "match + x" or "match [x]" case
    if (isMatchIdentifier(PsiTreeUtil.getDeepestFirst(statement))) {
      LanguageLevel languageLevel = LanguageLevel.forElement(statement);
      PyElementGenerator generator = PyElementGenerator.getInstance(statement.getProject());
      String subjectSuspect = StringUtil.trimStart(statement.getText(), PyNames.MATCH);
      try {
        generator.createExpressionFromText(languageLevel, subjectSuspect.trim());
        return Couple.of(statement, statement);
      }
      catch (IncorrectOperationException ignored) {
      }
    }
    return Couple.getEmpty();
  }

  private static boolean isMatchIdentifier(@NotNull PsiElement element) {
    return element.getText().equals(PyNames.MATCH);
  }
}
