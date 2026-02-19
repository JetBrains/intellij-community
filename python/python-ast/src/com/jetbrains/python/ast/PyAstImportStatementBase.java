// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;


@ApiStatus.Experimental
public interface PyAstImportStatementBase extends PyAstStatement {
  /**
   * @return elements that constitute the "import" clause
   */
  PyAstImportElement @NotNull [] getImportElements();

  /**
   * @return qualified names of imported elements regardless way they were imported.
   * "from bar import foo" or "import bar.foo" or "from bar import foo as spam" are all "bar.foo"
   */
  @NotNull
  List<String> getFullyQualifiedObjectNames();
}
