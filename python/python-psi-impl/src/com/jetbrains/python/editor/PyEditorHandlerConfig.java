// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.editor;

import com.intellij.psi.PsiComment;
import com.jetbrains.python.psi.*;

public final class PyEditorHandlerConfig {
  private PyEditorHandlerConfig() {
  }

  public static final Class[] IMPLICIT_WRAP_CLASSES = new Class[] {
    PyListLiteralExpression.class,
    PySetLiteralExpression.class,
    PyDictLiteralExpression.class,
    PyDictLiteralExpression.class,
    PyParenthesizedExpression.class,
    PyArgumentList.class,
    PyParameterList.class
  };
  static final Class[] WRAPPABLE_CLASSES = new Class[]{
    PsiComment.class,
    PyParenthesizedExpression.class,
    PyListCompExpression.class,
    PyDictCompExpression.class,
    PySetCompExpression.class,
    PyDictLiteralExpression.class,
    PySetLiteralExpression.class,
    PyListLiteralExpression.class,
    PyArgumentList.class,
    PyParameterList.class,
    PyDecoratorList.class,
    PySliceExpression.class,
    PySubscriptionExpression.class,
    PyGeneratorExpression.class
  };
}
