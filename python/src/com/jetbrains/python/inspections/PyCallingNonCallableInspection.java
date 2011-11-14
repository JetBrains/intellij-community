package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.cython.CythonLanguageDialect;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyCallingNonCallableInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Trying to call a non-callable object";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@NotNull ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      super.visitPyCallExpression(node);
      checkCallable(node, node.getCallee(), null);
    }

    @Override
    public void visitPyDecoratorList(PyDecoratorList node) {
      super.visitPyDecoratorList(node);
      for (PyDecorator decorator : node.getDecorators()) {
        final PyExpression callee = decorator.getCallee();
        checkCallable(decorator, callee, null);
        if (decorator.hasArgumentList()) {
          final PyCallExpression.PyMarkedCallee marked = decorator.resolveCallee(resolveWithoutImplicits());
          if (marked != null) {
            final Callable callable = marked.getCallable();
            final PyReferenceExpression callSite = callee instanceof PyReferenceExpression ? (PyReferenceExpression)callee : null;
            checkCallable(decorator, null, callable.getReturnType(myTypeEvalContext, callSite));
          }
        }
      }
    }

    private void checkCallable(@NotNull PyElement node, @Nullable PyExpression callee, @Nullable PyType type) {
      if (CythonLanguageDialect._isDisabledFor(node)) {
        return;
      }
      final Boolean callable = callee != null ? isCallable(callee, myTypeEvalContext) : isCallable(type, node);
      if (callable == null) {
        return;
      }
      if (!callable) {
        final PyType calleeType = callee != null ? callee.getType(myTypeEvalContext) : type;
        if (calleeType instanceof PyClassType) {
          registerProblem(node, String.format("'%s' object is not callable", calleeType.getName()));
        }
        else if (callee != null) {
          registerProblem(node, String.format("'%s' is not callable", callee.getName()));
        }
        else {
          registerProblem(node, "Expression is not callable");
        }
      }
    }
  }

  @Nullable
  private static Boolean isCallable(@NotNull PyExpression element, @NotNull TypeEvalContext context) {
    if (element instanceof PyQualifiedExpression && PyNames.CLASS.equals(element.getName())) {
      return true;
    }
    return isCallable(element.getType(context), element);
  }

  @Nullable static Boolean isCallable(@Nullable PyType type, @Nullable PsiElement anchor) {
    if (type == null || type instanceof PyTypeReference) {
      return null;
    }
    else if (type instanceof PyClassType) {
      final PyClassType classType = (PyClassType)type;
      if (classType.isDefinition()) {
        return true;
      }
      if (isMethodType(classType, anchor)) {
        return true;
      }
      final PyClass cls = classType.getPyClass();
      if (cls == null) {
        return null;
      }
      if (PyABCUtil.isSubclass(cls, PyNames.CALLABLE)) {
        return true;
      }
    }
    else if (type instanceof PyCallableType) {
      return true;
    }
    return false;
  }

  private static boolean isMethodType(PyClassType type, @Nullable PsiElement anchor) {
    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(anchor);
    return type.equals(builtinCache.getClassMethodType()) || type.equals(builtinCache.getStaticMethodType());
  }
}
