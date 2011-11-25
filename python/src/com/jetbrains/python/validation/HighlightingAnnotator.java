package com.jetbrains.python.validation;

import com.intellij.lang.annotation.Annotation;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.*;

import java.util.Set;

/**
 * @author yole
 */
public class HighlightingAnnotator extends PyAnnotator {
  @Override
  public void visitPyParameter(PyParameter node) {
    PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
    if (function != null) {
      Annotation annotation = getHolder().createInfoAnnotation(node, null);
      annotation.setTextAttributes(isSelf(node, function) ? PyHighlighter.PY_SELF_PARAMETER : PyHighlighter.PY_PARAMETER);
    }
  }

  @Override
  public void visitPyReferenceExpression(PyReferenceExpression node) {
    if (node.getQualifier() == null) {
      PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
      if (function != null) {
        final PyNamedParameter element = (PyNamedParameter) function.getParameterList().getElementNamed(node.getReferencedName());
        if (element != null) {
          Annotation annotation = getHolder().createInfoAnnotation(node, null);
          annotation.setTextAttributes(isSelf(element, function) ? PyHighlighter.PY_SELF_PARAMETER : PyHighlighter.PY_PARAMETER);
        }
      }
    }
  }

  private static boolean isSelf(PyParameter node, PyFunction function) {
    boolean isSelf = false;
    final int index = ArrayUtil.find(function.getParameterList().getParameters(), node);
    if (function.getContainingClass() != null && index == 0) {
      final Set<PyFunction.Flag> flags = PyUtil.detectDecorationsAndWrappersOf(function);
      if (!flags.contains(PyFunction.Flag.CLASSMETHOD) && !flags.contains(PyFunction.Flag.STATICMETHOD)) {
        isSelf = true;
      }
    }
    return isSelf;
  }
}
