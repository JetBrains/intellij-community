// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;


import com.intellij.psi.util.QualifiedName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Experimental
public interface PyAstImportStatement extends PyAstImportStatementBase, PyAstImplicitImportNameDefiner {
  @NotNull
  @Override
  default List<String> getFullyQualifiedObjectNames() {
    return getImportElementNames(getImportElements());
  }

  /**
   * Returns list of qualified names of import elements filtering out nulls
   * @param elements import elements
   * @return list of qualified names
   */
  @NotNull
  static List<String> getImportElementNames(final PyAstImportElement @NotNull ... elements) {
    final List<String> result = new ArrayList<>(elements.length);
    for (final PyAstImportElement element : elements) {
      final QualifiedName qName = element.getImportedQName();
      if (qName != null) {
        result.add(qName.toString());
      }
    }
    return result;
  }
}
