package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.quickfix.PyMoveAttributeToInitQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyClassImpl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * User: ktisha
 *
 * Inspection to detect situations, where instance attribute
 * defined outside __init__ function
 */
public class PyAttributeOutsideInitInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.attribute.outside.init");
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
    public void visitPyFunction(PyFunction node) {
      final PyClass containingClass = node.getContainingClass();
      if (containingClass == null) return;

      Map<String, PyTargetExpression> attributesInInit = new HashMap<String, PyTargetExpression>();
      final PyFunction initMethod = containingClass.findMethodByName(PyNames.INIT, true);
      if (initMethod != null)
        PyClassImpl.collectInstanceAttributes(initMethod, attributesInInit);

      Map<String, PyTargetExpression> attributes = new HashMap<String, PyTargetExpression>();
      PyClassImpl.collectInstanceAttributes(node, attributes);

      for (Map.Entry<String, PyTargetExpression> attribute : attributes.entrySet()) {
        if (!attributesInInit.containsKey(attribute.getKey())) {
          registerProblem(attribute.getValue(), PyBundle.message("INSP.attribute.$0.outside.init", attribute.getKey()),
                          new PyMoveAttributeToInitQuickFix());
        }
      }
    }

  }
}
