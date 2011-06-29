package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@NotNull final ProblemsHolder holder, LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      super.visitPyCallExpression(node);
      PyExpression callee = node.getCallee();
      if (callee != null) {
        PyType calleeType = myTypeEvalContext.getType(callee);
        if (calleeType instanceof PyClassType) {
          PyClassType classType = (PyClassType) calleeType;
          PyClass cls = classType.getPyClass();
          if (isMethodType(node, classType)) {
            return;
          }
          if (cls != null && !cls.isSubclass(PyNames.CALLABLE)) {
            registerProblem(node, String.format("'%s' object is not callable", cls.getName()));
          }
        }
        else if (calleeType != null) {
          registerProblem(node, String.format("'%s' is not callable", callee.getName()));
        }
      }
    }

    private static boolean isMethodType(PyCallExpression node, PyClassType classType) {
      final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(node);
      return classType.equals(builtinCache.getClassMethodType()) || classType.equals(builtinCache.getStaticMethodType());
    }
  }
}
