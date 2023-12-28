// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.psi.PyComprehensionForComponent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Generator expression PSI.
 */
@ApiStatus.Experimental
public interface PyAstGeneratorExpression extends PyAstComprehensionElement {
  @Override
  @NotNull
  default List<PsiNamedElement> getNamedElements() {
    // extract whatever names are defined in "for" components
    List<? extends PyComprehensionForComponent> fors = getForComponents();
    PyAstExpression[] for_targets = new PyAstExpression[fors.size()];
    int i = 0;
    for (PyComprehensionForComponent for_comp : fors) {
      for_targets[i] = for_comp.getIteratorVariable();
      i += 1;
    }
    final List<PyAstExpression> expressions = PyUtilCore.flattenedParensAndStars(for_targets);
    final List<PsiNamedElement> results = new ArrayList<>();
    for (PyAstExpression expression : expressions) {
      if (expression instanceof PsiNamedElement) {
        results.add((PsiNamedElement)expression);
      }
    }
    return results;
  }
}
