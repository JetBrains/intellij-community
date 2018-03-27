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
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 */
public class PySplitIfIntention extends PyBaseIntentionAction {
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.split.if");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    PsiElement elementAtOffset = file.findElementAt(editor.getCaretModel().getOffset());
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
    setText(PyBundle.message("INTN.split.if.text"));
    return true;
  }

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
