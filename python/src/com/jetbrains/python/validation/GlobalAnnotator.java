package com.jetbrains.python.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * Annotates errors in 'global' statements.
 */
public class GlobalAnnotator extends PyAnnotator {
  @Override
  public void visitPyGlobalStatement(final PyGlobalStatement node) {
    PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
    if (function != null) {
      PyParameterList paramList = function.getParameterList();
      // collect param names
      final Set<String> paramNames = new HashSet<String>();

      ParamHelper.walkDownParamArray(
        paramList.getParameters(),
        new ParamHelper.ParamVisitor() {
          @Override
          public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
            paramNames.add(param.getName());
          }
        }
      );

      // check globals
      final AnnotationHolder holder = getHolder();
      for (PyTargetExpression expr : node.getGlobals()) {
        final String expr_name = expr.getReferencedName();
        if (paramNames.contains(expr_name)) {
          holder.createErrorAnnotation(expr.getTextRange(), PyBundle.message("ANN.$0.both.global.and.param", expr_name));
        }
      }
    }
  }
}
