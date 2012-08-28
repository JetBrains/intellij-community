package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.TargetElementEvaluator;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyTargetElementEvaluator implements TargetElementEvaluator {
  @Override
  public boolean includeSelfInGotoImplementation(@NotNull PsiElement element) {
    return false;
  }

  @Nullable
  @Override
  public PsiElement getElementByReference(PsiReference ref, int flags) {
    if ((flags & TargetElementUtilBase.ELEMENT_NAME_ACCEPTED) == 0){
      return null;
    }
    final PsiElement element = ref.getElement();
    PsiElement result = ref.resolve();
    if (result instanceof PyReferenceExpression &&
        PsiTreeUtil.getParentOfType(element, ScopeOwner.class) == PsiTreeUtil.getParentOfType(result, ScopeOwner.class)) {
      QualifiedResolveResult resolveResult = ((PyReferenceExpression)result).followAssignmentsChain(PyResolveContext.noImplicits());
      PsiElement finalResult = resolveResult.getElement();
      if (PsiTreeUtil.getParentOfType(element, ScopeOwner.class) == PsiTreeUtil.getParentOfType(finalResult, ScopeOwner.class)) {
        return finalResult;
      }
    }
    return result;
  }
}
