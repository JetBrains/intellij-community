// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.jetbrains.python.ast.PyAstAnnotation;
import com.jetbrains.python.ast.PyAstExpressionStatement;
import com.jetbrains.python.ast.PyAstMatchStatement;
import com.jetbrains.python.ast.PyAstStatement;
import com.jetbrains.python.ast.PyAstTypeDeclarationStatement;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAstElementGenerator;
import com.jetbrains.python.psi.PyIndentUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public final class PyMatchStatementFixer extends PyFixer<PyAstStatement> {
  public PyMatchStatementFixer() {
    super(PyAstStatement.class);
  }

  @Override
  protected void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyAstStatement element) {
    Document document = editor.getDocument();
    PyAstMatchStatement matchStatement = tryCast(element, PyAstMatchStatement.class);
    if (matchStatement != null) {
      PsiElement colon = PyPsiUtilsCore.getFirstChildOfType(element, PyTokenTypes.COLON);
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

    PyAstTypeDeclarationStatement typeDeclaration = tryCast(element, PyAstTypeDeclarationStatement.class);
    // "match:" case
    if (typeDeclaration != null && isMatchIdentifier(typeDeclaration.getTarget())) {
      PyAstAnnotation annotation = typeDeclaration.getAnnotation();
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

  private static @NotNull Couple<PsiElement> findMatchKeywordAndSubjectInExpressionStatement(@NotNull PyAstStatement statement) {
    if (!(statement instanceof PyAstExpressionStatement)) return Couple.getEmpty();
    // "match <caret>expr" case
    PsiElement prevSibling = PyPsiUtilsCore.getPrevNonWhitespaceSiblingOnSameLine(statement);
    if (prevSibling instanceof PyAstExpressionStatement && isMatchIdentifier(prevSibling)) {
      return Couple.of(prevSibling, statement);
    }
    if (isMatchIdentifier(statement)) {
      // "<caret>match expr"
      PsiElement nextSibling = PyPsiUtilsCore.getNextNonWhitespaceSiblingOnSameLine(statement);
      if (nextSibling instanceof PyAstExpressionStatement) {
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
      PyAstElementGenerator generator = PyAstElementGenerator.getInstance(statement.getProject());
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
