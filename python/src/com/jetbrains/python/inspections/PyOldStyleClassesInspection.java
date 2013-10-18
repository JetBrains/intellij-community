package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: catherine
 *
 * Inspection to detect occurrences of new-style class features in old-style classes
 */
public class PyOldStyleClassesInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.oldstyle.class");
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
    public void visitPyClass(final PyClass node) {
      if (!node.isNewStyleClass()) {
        for (PyTargetExpression attr : node.getClassAttributes()) {
          if ("__slots__".equals(attr.getName())) {
            registerProblem(attr, "Old-style class contains __slots__ definition");
          }
        }
        for (PyFunction attr : node.getMethods()) {
          if ("__getattribute__".equals(attr.getName())) {
            registerProblem(attr, "Old-style class contains __getattribute__ definition");
          }
        }
      }
    }

    @Override
    public void visitPyCallExpression(final PyCallExpression node) {
      PyClass klass = PsiTreeUtil.getParentOfType(node, PyClass.class);
      if (klass != null && !klass.isNewStyleClass()) {
        final List<PyClassLikeType> types = klass.getSuperClassTypes(myTypeEvalContext);
        for (PyClassLikeType type : types) {
          if (type == null) return;
          final String qName = type.getClassQName();
          if (qName != null && qName.contains("PyQt")) return;
          if (!(type instanceof PyClassType)) return;
        }

        if (PyUtil.isSuperCall(node))
          registerProblem(node.getCallee(), "Old-style class contains call for super method");
      }
    }
  }
}
