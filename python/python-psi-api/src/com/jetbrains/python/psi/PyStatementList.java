// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;


import com.jetbrains.python.ast.PyAstStatementList;

public interface PyStatementList extends PyAstStatementList, PyElement {
  PyStatementList[] EMPTY_ARRAY = new PyStatementList[0];

  @Override
  PyStatement[] getStatements();
}
