// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.lang.ASTNode;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class PyConvertToNewStyleQuickFix extends PsiUpdateModCommandQuickFix {
  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.convert.to.new.style");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
    assert pyClass != null;

    final PyElementGenerator generator = PyElementGenerator.getInstance(project);
    final PyArgumentList expressionList = pyClass.getSuperClassExpressionList();
    if (expressionList != null) {
      final PyExpression object = generator.createExpressionFromText(LanguageLevel.forElement(element), "object");
      expressionList.addArgumentFirst(object);
    }
    else {
      final PyArgumentList list =
        generator.createFromText(LanguageLevel.forElement(element), PyClass.class, "class A(object):pass").getSuperClassExpressionList();
      assert list != null;
      final ASTNode node = pyClass.getNameNode();
      assert node != null;
      final PsiElement oldArgList = node.getPsi().getNextSibling();
      if (oldArgList instanceof PyArgumentList) {
        oldArgList.replace(list);
      }
    }
  }
}
