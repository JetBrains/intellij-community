package com.jetbrains.python.codeInsight.liveTemplates;

import com.intellij.codeInsight.template.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyFunctionNameMacro extends Macro {
  @Override
  public String getName() {
    return "pyFunctionName";
  }

  @Override
  public String getPresentableName() {
    return "pyFunctionName()";
  }

  @Nullable
  @Override
  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    PsiElement place = context.getPsiElementAtStartOffset();
    PyFunction pyFunction = PsiTreeUtil.getParentOfType(place, PyFunction.class);
    if (pyFunction == null) {
      return null;
    }
    String name = pyFunction.getName();
    return name == null ? null : new TextResult(name);
  }
}
