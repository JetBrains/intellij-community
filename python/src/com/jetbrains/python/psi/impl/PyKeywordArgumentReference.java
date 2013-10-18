package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * @author yole
 */
public class PyKeywordArgumentReference extends PsiReferenceBase.Poly<PyKeywordArgument> {
  public PyKeywordArgumentReference(@NotNull PyKeywordArgument element, TextRange textRange) {
    super(element, textRange, true);
  }

  @NotNull
  @Override
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    final String keyword = myElement.getKeyword();
    if (keyword == null) {
      return ResolveResult.EMPTY_ARRAY;
    }
    PsiElement call = PsiTreeUtil.getParentOfType(myElement, PyCallExpression.class, PyClass.class);
    if (!(call instanceof PyCallExpression)) {
      return ResolveResult.EMPTY_ARRAY;
    }
    final PyExpression callee = ((PyCallExpression)call).getCallee();
    if (callee == null) return ResolveResult.EMPTY_ARRAY;
    final PsiPolyVariantReference calleeReference = (PsiPolyVariantReference) callee.getReference();
    if (calleeReference == null) return ResolveResult.EMPTY_ARRAY;
    final ResolveResult[] calleeCandidates = calleeReference.multiResolve(incompleteCode);
    List<ResolveResult> resultList = new ArrayList<ResolveResult>();
    for (ResolveResult calleeCandidate : calleeCandidates) {
      if (!calleeCandidate.isValidResult()) continue;
      final PsiElement element = calleeCandidate.getElement();
      if (element == null) continue;
      final PyFunction calleeFunction = resolveToFunction(element, new HashSet<PsiElement>());
      if (calleeFunction != null) {
        final PsiElement result = calleeFunction.getParameterList().findParameterByName(keyword);
        if (result != null) {
          resultList.add(new PsiElementResolveResult(result));
        }
      }
    }
    return resultList.toArray(new ResolveResult[resultList.size()]);
  }

  @Nullable
  private static PyFunction resolveToFunction(PsiElement element, HashSet<PsiElement> visited) {
    if (visited.contains(element)) {
      return null;
    }
    visited.add(element);
    if (element instanceof PyFunction) {
      return (PyFunction)element;
    }
    if (element instanceof PyTargetExpression) {
      final PyExpression assignedValue = ((PyTargetExpression)element).findAssignedValue();
      return resolveToFunction(assignedValue, visited);
    }
    if (element instanceof PyReferenceExpression) {
      final PsiElement resolveResult = ((PyReferenceExpression)element).getReference().resolve();
      return resolveToFunction(resolveResult, visited);
    }
    return null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return new Object[0];  //To change body of implemented methods use File | Settings | File Templates.
  }
}
