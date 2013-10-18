package com.jetbrains.python.validation;

import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;

/**
 * Checks for non-top-level star imports.
 */
public class ImportAnnotator extends PyAnnotator {
  @Override
  public void visitPyFromImportStatement(final PyFromImportStatement node) {
    if (node.isStarImport() && PsiTreeUtil.getParentOfType(node, PyFunction.class, PyClass.class) != null) {
      getHolder().createWarningAnnotation(node, PyBundle.message("ANN.star.import.at.top.only"));
    }
  }
}
