// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Experimental
public interface PyAstWithItem extends PyAstElement {
  PyAstWithItem[] EMPTY_ARRAY = new PyAstWithItem[0];

  @NotNull
  default PyAstExpression getExpression() {
    return (PyAstExpression)getFirstChild();
  }

  @Nullable
  default PyAstExpression getTarget() {
    ASTNode[] children = getNode().getChildren(null);
    boolean foundAs = false;
    for (ASTNode child : children) {
      if (child.getElementType() == PyTokenTypes.AS_KEYWORD) {
        foundAs = true;
      }
      else if (foundAs && child.getPsi() instanceof PyAstExpression) {
        return (PyAstExpression)child.getPsi();
      }
    }
    return null;
  }
}
