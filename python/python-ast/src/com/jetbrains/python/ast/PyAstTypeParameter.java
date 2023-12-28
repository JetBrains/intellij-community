// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Type Parameter that can be a part of {@link PyAstTypeParameterList}<br>
 * For more information see <a href="https://peps.python.org/pep-0695/">PEP 695</a>
 */
@ApiStatus.Experimental
public interface PyAstTypeParameter extends PyAstElement, PsiNameIdentifierOwner, PyAstTypedElement {

  enum Kind {
    TypeVar(0),
    TypeVarTuple(1),
    ParamSpec(2);

    private final int myIndex;

    Kind(int index) {
      myIndex = index;
    }

    public int getIndex() {
      return myIndex;
    }

    public static Kind fromIndex(int index) {
      return switch (index) {
        case 1 -> TypeVarTuple;
        case 2 -> ParamSpec;
        default -> TypeVar;
      };
    }
  }

  @Nullable
  default PyAstExpression getBoundExpression() {
    return PsiTreeUtil.getChildOfType(this, PyAstExpression.class);
  }

  /**
   * Returns the text of the bound expression of the type parameter
   * <p>
   * The text is taken from stub if the stub is presented.
   */
  @Nullable
  default String getBoundExpressionText() {
    PyAstExpression boundExpression = getBoundExpression();
    if (boundExpression != null) {
      return boundExpression.getText();
    }

    return null;
  }

  @NotNull
  default Kind getKind() {
    String paramText = getText();
    if (paramText.startsWith("**")) {
      return Kind.ParamSpec;
    }
    else if (paramText.startsWith("*")) {
      return Kind.TypeVarTuple;
    }
    else return Kind.TypeVar;
  }

  @Override
  @Nullable
  default String getName() {
    PsiElement identifier = getNameIdentifier();
    return identifier != null ? identifier.getText() : null;
  }

  @Override
  @Nullable
  default PsiElement getNameIdentifier() {
    ASTNode nameNode = getNode().findChildByType(PyTokenTypes.IDENTIFIER);
    return nameNode != null ? nameNode.getPsi() : null;
  }
}
