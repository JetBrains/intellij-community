package com.jetbrains.python.validation;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.patterns.Matcher;
import com.jetbrains.python.psi.patterns.ParentMatcher;

/**
 * Checks for non-top-level star imports.
 */
public class ImportAnnotator extends PyAnnotator {

  private static Matcher INAPPROPRIATE = new ParentMatcher(PyClass.class, PyFunction.class);

  @Override
  public void visitPyFromImportStatement(final PyFromImportStatement node) {
    if (node.isStarImport() && INAPPROPRIATE.search(node) != null) {
      getHolder().createWarningAnnotation(node, PyBundle.message("ANN.star.import.at.top.only"));
    }
  }
}
