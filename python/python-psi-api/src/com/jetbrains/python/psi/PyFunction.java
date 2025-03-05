// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.util.ArrayFactory;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.*;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


/**
 * Function declaration in source (the {@code def} and everything within).
 */
public interface PyFunction extends PyAstFunction, StubBasedPsiElement<PyFunctionStub>, PsiNameIdentifierOwner, PyCompoundStatement,
                                    PyDecoratable, PyCallable, PyStatementListContainer, PyPossibleClassMember,
                                    ScopeOwner, PyDocStringOwner, PyTypeCommentOwner, PyAnnotationOwner, PyTypeParameterListOwner,
                                    PyDeprecatable {

  PyFunction[] EMPTY_ARRAY = new PyFunction[0];
  ArrayFactory<PyFunction> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PyFunction[count];

  /**
   * Infers function's return type by analyzing <b>only return statements</b> (including implicit returns) in its control flow.
   * Does not consider yield statements or return type annotations.
   *
   * @see PyFunction#getInferredReturnType(TypeEvalContext)
   */
  @Nullable
  PyType getReturnStatementType(@NotNull TypeEvalContext context);

  /**
   * Infers function's return type by analyzing <b>return statements</b> (including implicit returns) and <b>yield expression</b>.
   * In contrast with {@link TypeEvalContext#getReturnType(PyCallable)} does not consider 
   * return type annotations or any other {@link PyTypeProvider}.
   * 
   * @apiNote Does not cache the result.
   */
  @ApiStatus.Internal
  @Nullable
  PyType getInferredReturnType(@NotNull TypeEvalContext context);

  /**
   * Returns a list of all function exit points that can return a value.
   * This includes explicit 'return' statements and statements that can complete
   * normally with an implicit 'return None', excluding statements that raise exceptions.
   *
   * @see PyFunction#getReturnStatementType(TypeEvalContext) 
   * @return List of exit point statements, in control flow order
   */
  @ApiStatus.Internal
  @NotNull
  List<PyStatement> getReturnPoints(@NotNull TypeEvalContext context);

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
  default @NotNull PyStatementList getStatementList() {
    return (PyStatementList)PyAstFunction.super.getStatementList();
  }

  @Override
  default @Nullable PyFunction asMethod() {
    return (PyFunction)PyAstFunction.super.asMethod();
  }

  @Override
  default @Nullable PyStringLiteralExpression getDocStringExpression() {
    return (PyStringLiteralExpression)PyAstFunction.super.getDocStringExpression();
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
  default @Nullable PyClass getContainingClass() {
    return (PyClass)PyAstFunction.super.getContainingClass();
  }
}
