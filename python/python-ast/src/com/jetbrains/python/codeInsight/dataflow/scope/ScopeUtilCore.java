package com.jetbrains.python.codeInsight.dataflow.scope;

import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.ast.*;
import com.jetbrains.python.ast.controlFlow.AstScopeOwner;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.util.PsiTreeUtil.getParentOfType;
import static com.intellij.psi.util.PsiTreeUtil.isAncestor;

@ApiStatus.Experimental
public final class ScopeUtilCore {
  /**
   * Return the scope owner for the element.
   *
   * Scope owner is not always the first ScopeOwner parent of the element. Some elements are resolved in outer scopes.
   *
   * This method does not access AST if underlying PSI is stub based.
   */
  @Nullable
  public static AstScopeOwner getScopeOwner(@Nullable final PsiElement element) {
    if (element == null) {
      return null;
    }
    if (element instanceof PyAstExpressionCodeFragment) {
      final PsiElement context = element.getContext();
      return context instanceof AstScopeOwner ? (AstScopeOwner)context : getScopeOwner(context);
    }
    if (element instanceof StubBasedPsiElement) {
      final StubElement stub = ((StubBasedPsiElement<?>)element).getStub();
      if (stub != null) {
        StubElement parentStub = stub.getParentStub();
        while (parentStub != null) {
          final PsiElement parent = parentStub.getPsi();
          if (parent instanceof AstScopeOwner) {
            return (AstScopeOwner)parent;
          }
          parentStub = parentStub.getParentStub();
        }
        return null;
      }
    }
    return CachedValuesManager.getCachedValue(element, () -> CachedValueProvider.Result
      .create(calculateScopeOwnerByAST(element), PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Nullable
  private static AstScopeOwner calculateScopeOwnerByAST(@Nullable PsiElement element) {
    final AstScopeOwner firstOwner = getParentOfType(element, AstScopeOwner.class);
    if (firstOwner == null) {
      return null;
    }
    final AstScopeOwner nextOwner = getParentOfType(firstOwner, AstScopeOwner.class);
    // References in decorator expressions are resolved outside of the function (if the lambda is not inside the decorator)
    final PyAstElement decoratorAncestor = getParentOfType(element, PyAstDecorator.class);
    if (decoratorAncestor != null && !isAncestor(decoratorAncestor, firstOwner, true)) {
      return nextOwner;
    }
    /*
     * References in default values are resolved outside of the function (if the lambda is not inside the default value).
     * Annotations of parameters are resolved outside of the function if the function doesn't have type parameters list
     */
    final PyAstNamedParameter parameterAncestor = getParentOfType(element, PyAstNamedParameter.class);
    if (parameterAncestor != null && !isAncestor(parameterAncestor, firstOwner, true)) {
      final PyAstExpression defaultValue = parameterAncestor.getDefaultValue();
      final PyAstAnnotation annotation = parameterAncestor.getAnnotation();
      if (firstOwner instanceof PyAstFunction function) {
        PyAstTypeParameterList typeParameterList = function.getTypeParameterList();
        if ((typeParameterList == null && isAncestor(annotation, element, false))
            || (isAncestor(defaultValue, element, false))) {
          return nextOwner;
        }
      }
    }
    // Superclasses are resolved outside of the class if the class doesn't have type parameters list
    final PyAstClass containingClass = getParentOfType(element, PyAstClass.class);
    if (containingClass != null && isAncestor(containingClass.getSuperClassExpressionList(), element, false) && containingClass.getTypeParameterList() == null) {
      return nextOwner;
    }
    // Function return annotations and type comments are resolved outside of the function if the function doesn't have type parameters list
    if (firstOwner instanceof PyAstFunction function) {
      PyAstTypeParameterList typeParameterList = function.getTypeParameterList();
      if ((typeParameterList == null && isAncestor(function.getAnnotation(), element, false)
           || isAncestor(function.getTypeComment(), element, false))) {
        return nextOwner;
      }
    }
    return firstOwner;
  }
}
