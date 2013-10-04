package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.quickfix.PyMoveAttributeToInitQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.testing.PythonUnitTestUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
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
      if (!isApplicable(containingClass)) {
        return;
      }
      final List<PyTargetExpression> classAttributes = containingClass.getClassAttributes();

      Map<String, PyTargetExpression> attributesInInit = new HashMap<String, PyTargetExpression>();
      for (PyTargetExpression classAttr : classAttributes) {
        attributesInInit.put(classAttr.getName(), classAttr);
      }

      final PyFunction initMethod = containingClass.findMethodByName(PyNames.INIT, false);
      if (initMethod != null) {
        PyClassImpl.collectInstanceAttributes(initMethod, attributesInInit);
        collectAttributesFromSuper(attributesInInit, initMethod);
      }
      else {
        for (PyClass superClass : containingClass.getAncestorClasses(myTypeEvalContext)) {
          final PyFunction superInit = superClass.findMethodByName(PyNames.INIT, false);
          if (superInit != null)
            PyClassImpl.collectInstanceAttributes(superInit, attributesInInit);
        }
      }

      Map<String, PyTargetExpression> attributes = new HashMap<String, PyTargetExpression>();
      PyClassImpl.collectInstanceAttributes(node, attributes);

      for (Map.Entry<String, PyTargetExpression> attribute : attributes.entrySet()) {
        final Property property = containingClass.findProperty(attribute.getKey());
        if (!attributesInInit.containsKey(attribute.getKey()) && property == null) {
          registerProblem(attribute.getValue(), PyBundle.message("INSP.attribute.$0.outside.init", attribute.getKey()),
                          new PyMoveAttributeToInitQuickFix());
        }
      }
    }

    private void collectAttributesFromSuper(Map<String, PyTargetExpression> attributesInInit, PyFunction initMethod) {
      final PyStatementList statementList = initMethod.getStatementList();
      if (statementList != null) {
        for (PyStatement statement : statementList.getStatements()) {
          if (statement instanceof PyExpressionStatement) {
            final PyExpression expression = ((PyExpressionStatement)statement).getExpression();
            if (expression instanceof PyCallExpression) {
              final PyType callType = PyCallExpressionHelper.getCallType((PyCallExpression)expression, myTypeEvalContext);
              if (callType instanceof PyClassType) {
                final PyClass superClass = ((PyClassType)callType).getPyClass();
                final PyFunction superInit = superClass.findMethodByName(PyNames.INIT, false);
                if (superInit != null) {
                  PyClassImpl.collectInstanceAttributes(superInit, attributesInInit);
                  collectAttributesFromSuper(attributesInInit, superInit);
                }
              }
            }
          }
        }
      }
    }
  }

  private static boolean isApplicable(@NotNull final PyClass containingClass) {
    return !PythonUnitTestUtil.isUnitTestCaseClass(containingClass) && !containingClass.isSubclass("django.db.models.base.Model");
  }
}
