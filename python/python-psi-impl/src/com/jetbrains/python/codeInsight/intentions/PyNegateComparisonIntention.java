// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.ConditionUtil;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyFile;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

public final class PyNegateComparisonIntention extends PyBaseIntentionAction {


  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.negate.comparison");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PyBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class, false);
    Pair<String, String> negation = ConditionUtil.findComparisonNegationOperators(binaryExpression);
    if (negation != null) {
      setText(PyPsiBundle.message("INTN.negate.comparison", negation.component1(), negation.component2()));
      return true;
    }
    return false;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PyBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class, false);
    ConditionUtil.negateComparisonExpression(project, file, binaryExpression);
  }
}
