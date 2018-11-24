/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public interface PyBinaryExpression extends PyQualifiedExpression, PyCallSiteExpression, PyReferenceOwner {

  @Nullable
  @Override
  default PyExpression getReceiver(@Nullable PyCallable resolvedCallee) {
    final boolean isRight = resolvedCallee != null && PyNames.isRightOperatorName(resolvedCallee.getName());
    return isRight ? getRightExpression() : getLeftExpression();
  }

  @NotNull
  @Override
  default List<PyExpression> getArguments(@Nullable PyCallable resolvedCallee) {
    final boolean isRight = resolvedCallee != null && PyNames.isRightOperatorName(resolvedCallee.getName());
    return Collections.singletonList(isRight ? getLeftExpression() : getRightExpression());
  }

  PyExpression getLeftExpression();
  @Nullable PyExpression getRightExpression();

  @Nullable
  PyElementType getOperator();

  @Nullable
  PsiElement getPsiOperator();

  boolean isOperator(String chars);

  PyExpression getOppositeExpression(PyExpression expression)
      throws IllegalArgumentException;
}
