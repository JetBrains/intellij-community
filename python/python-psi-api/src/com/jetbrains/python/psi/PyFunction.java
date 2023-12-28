// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.util.ArrayFactory;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Function declaration in source (the {@code def} and everything within).
 */
public interface PyFunction extends PyAstFunction, StubBasedPsiElement<PyFunctionStub>, PsiNameIdentifierOwner, PyCompoundStatement,
                                    PyDecoratable, PyCallable, PyStatementListContainer, PyPossibleClassMember,
                                    ScopeOwner, PyDocStringOwner, PyTypeCommentOwner, PyAnnotationOwner, PyTypeParameterListOwner {

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
  @NotNull
  default PyParameterList getParameterList() {
    return (PyParameterList)PyAstFunction.super.getParameterList();
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

  @Override
  @Nullable
  default PyTypeParameterList getTypeParameterList() {
    return (PyTypeParameterList)PyAstFunction.super.getTypeParameterList();
  }

  @Override
  @Nullable
  default PyDecoratorList getDecoratorList() {
    return (PyDecoratorList)PyAstFunction.super.getDecoratorList();
  }

  @Override
  @Nullable
  default PyAnnotation getAnnotation() {
    return (PyAnnotation)PyAstFunction.super.getAnnotation();
  }

  @Override
  @Nullable
  default PyClass getContainingClass() {
    return (PyClass)PyAstFunction.super.getContainingClass();
  }
}
