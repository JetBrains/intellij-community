package com.intellij.refactoring.introduceParameter;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiType;
import gnu.trove.TIntArrayList;

public interface IntroduceParameterData {
  @NotNull
  Project getProject();

  PsiMethod getMethodToReplaceIn();

  @NotNull
  PsiMethod getMethodToSearchFor();

  PsiExpression getParameterInitializer();

  PsiExpression getExpressionToSearch();

  PsiLocalVariable getLocalVariable();

  boolean isRemoveLocalVariable();

  @NotNull
  String getParameterName();

  boolean isReplaceAllOccurences();

  int getReplaceFieldsWithGetters();

  boolean isDeclareFinal();

  boolean isGenerateDelegate();

  PsiType getForcedType();

  @NotNull
  TIntArrayList getParametersToRemove();
}
