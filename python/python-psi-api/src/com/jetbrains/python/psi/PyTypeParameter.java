// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyTypeParameterStub;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Type Parameter that can be a part of {@link PyTypeParameterList}<br>
 * For more information see <a href="https://peps.python.org/pep-0695/">PEP 695</a>
 */
public interface PyTypeParameter extends PyElement, PsiNameIdentifierOwner, PyTypedElement, StubBasedPsiElement<PyTypeParameterStub> {

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
  PyExpression getBoundExpression();

  /**
   * Returns the text of the bound expression of the type parameter
   * <p>
   * The text is taken from stub if the stub is presented.
   */
  @Nullable
  String getBoundExpressionText();

  @NotNull
  PyTypeParameter.Kind getKind();
}
