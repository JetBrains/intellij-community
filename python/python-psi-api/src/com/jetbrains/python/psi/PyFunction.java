// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.util.ArrayFactory;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.*;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static com.jetbrains.python.ast.impl.PyDeprecationUtilKt.extractDeprecationMessageFromDecorator;

/**
 * Function declaration in source (the {@code def} and everything within).
 */
public interface PyFunction extends PyAstFunction, StubBasedPsiElement<PyFunctionStub>, PsiNameIdentifierOwner, PyCompoundStatement,
                                    PyDecoratable, PyCallable, PyStatementListContainer, PyPossibleClassMember,
                                    ScopeOwner, PyDocStringOwner, PyTypeCommentOwner, PyAnnotationOwner, PyTypeParameterListOwner,
                                    PyDeprecatable {

  PyFunction[] EMPTY_ARRAY = new PyFunction[0];
  ArrayFactory<PyFunction> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PyFunction[count];

  @Nullable
  PyType getReturnStatementType(@NotNull TypeEvalContext context);

  /**
   * Checks whether the function contains a yield expression in its body.
   */
  boolean isGenerator();

  /**
   * Returns a property for which this function is a getter, setter or deleter.
   *
   * @return the corresponding property, or null if there isn't any.
   */
  @Nullable
  Property getProperty();

  /**
   * Searches for function attributes.
   * See <a href="http://legacy.python.org/dev/peps/pep-0232/">PEP-0232</a>
   * @return assignment statements for function attributes
   */
  @NotNull
  List<PyAssignmentStatement> findAttributes();

  @Override
  @NotNull
  default PyStatementList getStatementList() {
    return (PyStatementList)PyAstFunction.super.getStatementList();
  }

  @Override
  @Nullable
  default PyFunction asMethod() {
    return (PyFunction)PyAstFunction.super.asMethod();
  }

  @Override
  @Nullable
  default PyStringLiteralExpression getDocStringExpression() {
    return (PyStringLiteralExpression)PyAstFunction.super.getDocStringExpression();
  }

  /**
   * If the function raises a DeprecationWarning or a PendingDeprecationWarning, returns the explanation text provided for the warning..
   *
   * @return the deprecation message or null if the function is not deprecated.
   */
  @Nullable
  @Override
  default String getDeprecationMessage() {
    return extractDeprecationMessage();
  }

  @Nullable
  default String extractDeprecationMessage() {
    String deprecationMessageFromDecorator = extractDeprecationMessageFromDecorator(this);
    if (deprecationMessageFromDecorator != null) {
      return deprecationMessageFromDecorator;
    }
    PyAstStatementList statementList = getStatementList();
    return extractDeprecationMessage(Arrays.asList(statementList.getStatements()));
  }

  static @Nullable String extractDeprecationMessage(List<? extends PyAstStatement> statements) {
    for (PyAstStatement statement : statements) {
      if (statement instanceof PyAstExpressionStatement expressionStatement) {
        if (expressionStatement.getExpression() instanceof PyAstCallExpression callExpression) {
          if (callExpression.isCalleeText(PyNames.WARN)) {
            PyAstReferenceExpression warningClass = callExpression.getArgument(1, PyAstReferenceExpression.class);
            if (warningClass != null && (PyNames.DEPRECATION_WARNING.equals(warningClass.getReferencedName()) ||
                                         PyNames.PENDING_DEPRECATION_WARNING.equals(warningClass.getReferencedName()))) {
              return PyPsiUtilsCore.strValue(callExpression.getArguments()[0]);
            }
          }
        }
      }
    }
    return null;
  }

  @Override
  @Nullable
  default PyClass getContainingClass() {
    return (PyClass)PyAstFunction.super.getContainingClass();
  }
}
