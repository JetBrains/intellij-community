// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

public final class PySplitIfIntention extends PyBaseIntentionAction {
  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.split.if");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (!(psiFile instanceof PyFile)) {
      return false;
    }

    PsiElement elementAtOffset = psiFile.findElementAt(editor.getCaretModel().getOffset());
    if (elementAtOffset == null || elementAtOffset.getNode() == null) {
      return false;
    }

    // PY-745
    final IElementType elementType = elementAtOffset.getNode().getElementType();
    if (elementType == PyTokenTypes.COLON) {
      elementAtOffset = elementAtOffset.getPrevSibling();
      elementAtOffset = PyPsiUtils.getPrevNonCommentSibling(elementAtOffset, false);
    }
    else if (elementType == PyTokenTypes.IF_KEYWORD) {
      elementAtOffset = elementAtOffset.getNextSibling();
      elementAtOffset = PyPsiUtils.getNextNonCommentSibling(elementAtOffset, false);
    }

    PsiElement element = PsiTreeUtil.getParentOfType(elementAtOffset, PyBinaryExpression.class, false);
    if (element == null) {
      return false;
    }

    while (element.getParent() instanceof PyBinaryExpression) {
      element = element.getParent();
    }
    if (((PyBinaryExpression)element).getOperator() != PyTokenTypes.AND_KEYWORD
        || ((PyBinaryExpression) element).getRightExpression() == null) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PyIfPart)) {
      return false;
    }
    setText(PyPsiBundle.message("INTN.split.if"));
    return true;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement elementAtOffset = file.findElementAt(editor.getCaretModel().getOffset());
    // PY-745
    final IElementType elementType = elementAtOffset.getNode().getElementType();
    if (elementType == PyTokenTypes.COLON) {
      elementAtOffset = elementAtOffset.getPrevSibling();
      elementAtOffset = PyPsiUtils.getPrevNonCommentSibling(elementAtOffset, false);
    }
    else if (elementType == PyTokenTypes.IF_KEYWORD) {
      elementAtOffset = elementAtOffset.getNextSibling();
      elementAtOffset = PyPsiUtils.getNextNonCommentSibling(elementAtOffset, false);
    }

    PyBinaryExpression element = PsiTreeUtil.getParentOfType(elementAtOffset, PyBinaryExpression.class, false);
    while (element.getParent() instanceof PyBinaryExpression) {
      element = (PyBinaryExpression)element.getParent();
    }
    PyIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PyIfStatement.class);
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    PyIfStatement subIf = (PyIfStatement) ifStatement.copy();

    subIf.getIfPart().getCondition().replace(element.getRightExpression());
    ifStatement.getIfPart().getCondition().replace(element.getLeftExpression());
    PyStatementList statementList = elementGenerator.createFromText(LanguageLevel.getDefault(), PyIfStatement.class, "if a:\n    a = 1").getIfPart().getStatementList();
    statementList.getStatements()[0].replace(subIf);
    PyIfStatement newIf = elementGenerator.createFromText(LanguageLevel.getDefault(), PyIfStatement.class, "if a:\n    a = 1");
    newIf.getIfPart().getCondition().replace(ifStatement.getIfPart().getCondition());
    newIf.getIfPart().getStatementList().replace(statementList);
    for (PyIfPart elif : ifStatement.getElifParts())
      newIf.add(elif);
    if (ifStatement.getElsePart() != null) {
        newIf.add(ifStatement.getElsePart());
    }
    ifStatement.replace(newIf);
  }
}
