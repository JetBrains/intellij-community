// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeCook.deductive.util;

import com.intellij.psi.*;
import com.intellij.refactoring.typeCook.Settings;
import com.intellij.refactoring.typeCook.Util;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

public class VictimCollector extends Visitor {
  final Set<PsiElement> myVictims = new LinkedHashSet<>();
  final PsiElement[] myElements;
  final Settings mySettings;

  public VictimCollector(final PsiElement[] elements, final Settings settings) {
    myElements = elements;
    mySettings = settings;
  }

  private void testNAdd(final PsiElement element, final PsiType t) {
    if (Util.isRaw(t, mySettings)) {
      if (element instanceof PsiNewExpression && t.getCanonicalText().equals(CommonClassNames.JAVA_LANG_OBJECT)){
        return;  
      }

      myVictims.add(element);
    }
  }

  @Override public void visitLocalVariable(final @NotNull PsiLocalVariable variable) {
    testNAdd(variable, variable.getType());

    super.visitLocalVariable(variable);
  }

  @Override public void visitForeachStatement(final @NotNull PsiForeachStatement statement) {
    super.visitForeachStatement(statement);
    final PsiParameter parameter = statement.getIterationParameter();
    testNAdd(parameter, parameter.getType());
  }

  @Override public void visitField(final @NotNull PsiField field) {
    testNAdd(field, field.getType());

    super.visitField(field);
  }

  @Override public void visitMethod(final @NotNull PsiMethod method) {
    final PsiParameter[] parms = method.getParameterList().getParameters();

    for (PsiParameter parm : parms) {
      testNAdd(parm, parm.getType());
    }

    if (Util.isRaw(method.getReturnType(), mySettings)) {
      myVictims.add(method);
    }

    final PsiCodeBlock body = method.getBody();

    if (body != null) {
      body.accept(this);
    }
  }

  @Override public void visitNewExpression(final @NotNull PsiNewExpression expression) {
    if (expression.getClassReference() != null) {
      testNAdd(expression, expression.getType());
    }

    super.visitNewExpression(expression);
  }

  @Override public void visitTypeCastExpression (final @NotNull PsiTypeCastExpression cast){
    final PsiTypeElement typeElement = cast.getCastType();
    if (typeElement != null) {
      testNAdd(cast, typeElement.getType());
    }

    super.visitTypeCastExpression(cast);
  }

  @Override public void visitReferenceExpression(final @NotNull PsiReferenceExpression expression) {
  }

  @Override public void visitFile(@NotNull PsiFile psiFile) {
    if (psiFile instanceof PsiJavaFile) {
      super.visitFile(psiFile);
    }
  }

  public Set<PsiElement> getVictims() {
    for (PsiElement element : myElements) {
      element.accept(this);
    }

    return myVictims;
  }
}
