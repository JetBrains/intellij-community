// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyElementTypes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;
import static com.jetbrains.python.ast.PyAstElementKt.findChildrenByType;

@ApiStatus.Experimental
public interface PyAstMatchStatement extends PyAstCompoundStatement {
  default @Nullable PyAstExpression getSubject() {
    return findChildByClass(this, PyAstExpression.class);
  }

  default @NotNull List<? extends PyAstCaseClause> getCaseClauses() {
    return findChildrenByType(this, PyElementTypes.CASE_CLAUSE);
  }
}
