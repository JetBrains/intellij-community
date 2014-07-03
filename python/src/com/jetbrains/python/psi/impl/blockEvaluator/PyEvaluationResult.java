package com.jetbrains.python.psi.impl.blockEvaluator;

import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Ilya.Kazakevich
 */
@SuppressWarnings("PackageVisibleField") // Package-only class
class PyEvaluationResult {
  @NotNull
  final Map<String, Object> myNamespace = new HashMap<String, Object>();
  @NotNull
  final Map<String, List<PyExpression>> myDeclarations = new HashMap<String, List<PyExpression>>();

  @NotNull
  List<PyExpression> getDeclarations(@NotNull final String name) {
    final List<PyExpression> expressions = myDeclarations.get(name);
    return (expressions != null) ? expressions : Collections.<PyExpression>emptyList();
  }
}
