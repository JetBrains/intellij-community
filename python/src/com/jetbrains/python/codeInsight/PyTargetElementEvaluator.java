package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.TargetElementEvaluator;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

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
    Set<PsiElement> visited = new HashSet<PsiElement>();
    visited.add(result);
    while (result instanceof PyReferenceExpression || result instanceof PyTargetExpression) {
      PsiElement nextResult = ((PyQualifiedExpression) result).getReference(PyResolveContext.noImplicits()).resolve();
      if (nextResult != null && !visited.contains(nextResult) &&
          PsiTreeUtil.getParentOfType(element, ScopeOwner.class) == PsiTreeUtil.getParentOfType(result, ScopeOwner.class) &&
          (nextResult instanceof PyReferenceExpression || nextResult instanceof PyTargetExpression || nextResult instanceof PyParameter)) {
        visited.add(nextResult);
        result = nextResult;
      }
      else {
        break;
      }
    }
    return result;
  }
}
