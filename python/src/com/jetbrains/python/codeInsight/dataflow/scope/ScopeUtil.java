package com.jetbrains.python.codeInsight.dataflow.scope;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyExceptPartNavigator;
import com.jetbrains.python.psi.impl.PyForStatementNavigator;
import com.jetbrains.python.psi.impl.PyListCompExpressionNavigator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author oleg
 */
public class ScopeUtil {
  private ScopeUtil() {
  }

  @Nullable
  public static PsiElement getParameterScope(final PsiElement element){
    if (element instanceof PyNamedParameter){
      final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
      if (function != null){
        return function;
      }
    }

    final PyExceptPart exceptPart = PyExceptPartNavigator.getPyExceptPartByTarget(element);
    if (exceptPart != null){
      return exceptPart;
    }

    final PyForStatement forStatement = PyForStatementNavigator.getPyForStatementByIterable(element);
    if (forStatement != null){
      return forStatement;
    }

    final PyListCompExpression listCompExpression = PyListCompExpressionNavigator.getPyListCompExpressionByVariable(element);
    if (listCompExpression != null){
      return listCompExpression;
    }
    return null;
  }

  @Nullable
  public static ScopeOwner getScopeOwner(PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, ScopeOwner.class);
  }

  @Nullable
  public static ScopeOwner getResolveScopeOwner(PsiElement element) {
    // References in default values of parameters are defined somewhere in outer scopes, as well as references in decorators (if they are
    // not inside a lambda, see PY-6083) and superclasses
    final ScopeOwner initialScopeOwner = getScopeOwner(element);
    if (!(initialScopeOwner instanceof PyLambdaExpression && PsiTreeUtil.getParentOfType(element, PyDecorator.class) != null) &&
        PsiTreeUtil.getParentOfType(element, PyParameter.class, PyDecorator.class) != null) {
      element = initialScopeOwner;
    }
    final PyClass containingClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
    if (containingClass != null && element != null &&
        PsiTreeUtil.isAncestor(containingClass.getSuperClassExpressionList(), element, false)) {
      element = containingClass;
    }
    return PsiTreeUtil.getParentOfType(element, ScopeOwner.class);
  }

  @Nullable
  public static ScopeOwner getDeclarationScopeOwner(PsiElement anchor, String name) {
    PsiElement element = anchor;
    if (name != null) {
      // References in default values of parameters are defined somewhere in outer scopes, as well as references in decorators
      if (PsiTreeUtil.getParentOfType(anchor, PyParameter.class, PyDecorator.class) != null) {
        element = getScopeOwner(anchor);
      }
      final ScopeOwner originalScopeOwner = getScopeOwner(element);
      ScopeOwner scopeOwner = originalScopeOwner;
      while (scopeOwner != null) {
        if (!(scopeOwner instanceof PyClass) || scopeOwner == originalScopeOwner) {
          Scope scope = ControlFlowCache.getScope(scopeOwner);
          if (scope.containsDeclaration(name)) {
            return scopeOwner;
          }
        }
        scopeOwner = getScopeOwner(scopeOwner);
      }
    }
    return null;
  }

  @NotNull
  public static Collection<PsiElement> getReadWriteElements(@NotNull String name, @NotNull ScopeOwner scopeOwner, boolean isReadAccess,
                                                            boolean isWriteAccess) {
    ControlFlow flow = ControlFlowCache.getControlFlow(scopeOwner);
    Collection<PsiElement> result = new ArrayList<PsiElement>();
    for (Instruction instr : flow.getInstructions()) {
      if (instr instanceof ReadWriteInstruction) {
        ReadWriteInstruction rw = (ReadWriteInstruction)instr;
        if (name.equals(rw.getName())) {
          ReadWriteInstruction.ACCESS access = rw.getAccess();
          if ((isReadAccess && access.isReadAccess()) || (isWriteAccess && access.isWriteAccess())) {
            result.add(rw.getElement());
          }
        }
      }
    }
    return result;
  }
}
