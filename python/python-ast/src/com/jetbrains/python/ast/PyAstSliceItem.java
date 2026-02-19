// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Experimental
public interface PyAstSliceItem extends PyAstExpression {
  default @Nullable PyAstExpression getLowerBound() {
    return getChildExpression(0);
  }

  default @Nullable PyAstExpression getUpperBound() {
    return getChildExpression(1);
  }

  default @Nullable PyAstExpression getStride() {
    return getChildExpression(2);
  }

  private @Nullable PyAstExpression getChildExpression(int index) {
    ASTNode[] children = getNode().getChildren(TokenSet.ANY);
    int i = 0;
    while (i < children.length && index > 0) {
      if (children[i].getElementType() == PyTokenTypes.COLON) {
        i++;
      }
      else {
        i += 2;
      }
      index--;
    }
    if (i < children.length && children[i].getElementType() != PyTokenTypes.COLON) {
      return (PyAstExpression)children[i].getPsi();
    }
    return null;
  }
}
