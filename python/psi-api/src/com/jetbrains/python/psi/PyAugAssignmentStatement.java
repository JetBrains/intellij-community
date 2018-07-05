/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyAugAssignmentStatement extends PyStatement, PyQualifiedElement {
  @NotNull
  PyExpression getTarget();
  @Nullable
  PyExpression getValue();

  /**
   * @deprecated This method will be removed in 2019.1.
   */
  @Nullable
  @Deprecated
  PsiElement getOperation();

  /**
   * @apiNote This method will be marked as abstract in 2019.1.
   */
  @Nullable
  default PyElementType getOperator() {
    return null;
  }

  /**
   * @apiNote This method will be marked as abstract in 2019.1.
   */
  @Nullable
  default PsiElement getPsiOperator() {
    return getOperation();
  }

  /**
   * @apiNote This method will be marked as abstract in 2019.1.
   */
  default boolean isRightOperator(@Nullable PyCallable resolvedCallee) {
    final String calleeName = resolvedCallee == null ? null : resolvedCallee.getName();
    return calleeName != null && calleeName.startsWith("__r");
  }
}
