package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil;
import com.jetbrains.python.inspections.quickfix.PyImplementMethodsQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * User: ktisha
 */
public class PyAbstractClassInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.abstract.class");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyClass(PyClass node) {
      Set<PyFunction> toBeImplemented = new HashSet<PyFunction>();
      final Collection<PyFunction> functions = PyOverrideImplementUtil.getAllSuperFunctions(node);
      for (PyFunction method : functions) {
        final PyDecoratorList list = method.getDecoratorList();
        if (list != null && node.findMethodByName(method.getName(), false) == null) {
          if (list.findDecorator(PyNames.ABSTRACTMETHOD) != null || list.findDecorator(PyNames.ABSTRACTPROPERTY) != null) {
            toBeImplemented.add(method);
          }
        }
      }
      final ASTNode nameNode = node.getNameNode();
      if (!toBeImplemented.isEmpty() && nameNode != null) {
        registerProblem(nameNode.getPsi(), PyBundle.message("INSP.NAME.abstract.class.$0.must.implement", node.getName()),
                        ProblemHighlightType.INFO, null, new PyImplementMethodsQuickFix(node, toBeImplemented));
      }
    }
  }
}
