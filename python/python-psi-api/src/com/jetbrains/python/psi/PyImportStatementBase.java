// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

import java.util.List;


public interface PyImportStatementBase extends PyStatement {
  /**
   * @return elements that constitute the "import" clause
   */
  PyImportElement @NotNull [] getImportElements();

  /**
   * @return qualified names of imported elements regardless way they were imported.
   * "from bar import foo" or "import bar.foo" or "from bar import foo as spam" are all "bar.foo"
   */
  @NotNull
  List<String> getFullyQualifiedObjectNames();
}
