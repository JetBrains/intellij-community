// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.editor;

import com.intellij.psi.PsiComment;
import com.jetbrains.python.ast.*;
import org.jetbrains.annotations.ApiStatus;

public final class PyEditorHandlerConfig {
  private PyEditorHandlerConfig() {
  }

  public static final Class[] IMPLICIT_WRAP_CLASSES = new Class[] {
    PyAstListLiteralExpression.class,
    PyAstSetLiteralExpression.class,
    PyAstDictLiteralExpression.class,
    PyAstDictLiteralExpression.class,
    PyAstParenthesizedExpression.class,
    PyAstArgumentList.class,
    PyAstParameterList.class,
    PyAstGroupPattern.class,
    PyAstSequencePattern.class,
    PyAstMappingPattern.class,
    PyAstPatternArgumentList.class,
    PyAstTypeParameterList.class,
  };
  public static final Class[] WRAPPABLE_CLASSES = new Class[]{
    PsiComment.class,
    PyAstParenthesizedExpression.class,
    PyAstListCompExpression.class,
    PyAstDictCompExpression.class,
    PyAstSetCompExpression.class,
    PyAstDictLiteralExpression.class,
    PyAstSetLiteralExpression.class,
    PyAstListLiteralExpression.class,
    PyAstArgumentList.class,
    PyAstParameterList.class,
    PyAstDecoratorList.class,
    PyAstSubscriptionExpression.class,
    PyAstGeneratorExpression.class,
    PyAstGroupPattern.class,
    PyAstMappingPattern.class,
    PyAstPatternArgumentList.class,
    PyAstTypeParameterList.class,
  };

  @ApiStatus.Internal
  public static final Class[] CLASSES_TO_PARENTHESISE_ON_ENTER = new Class[]{
    PyAstBinaryExpression.class,
    PyAstCallExpression.class,
    PyAstFromImportStatement.class,
    PyAstTupleExpression.class,
    PyAstWithStatement.class,
    PyAstSequencePattern.class,
    PyAstReferenceExpression.class,
  };
}
