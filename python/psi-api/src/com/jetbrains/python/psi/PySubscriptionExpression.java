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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public interface PySubscriptionExpression extends PyQualifiedExpression, PyCallSiteExpression, PyReferenceOwner {

  @Nullable
  @Override
  default PyExpression getReceiver(@Nullable PyCallable resolvedCallee) {
    return getOperand();
  }

  @NotNull
  @Override
  default List<PyExpression> getArguments(@Nullable PyCallable resolvedCallee) {
    if (AccessDirection.of(this) == AccessDirection.WRITE) {
      final PsiElement parent = getParent();
      if (parent instanceof PyAssignmentStatement) {
        return Arrays.asList(getIndexExpression(), ((PyAssignmentStatement)parent).getAssignedValue());
      }
    }
    return Collections.singletonList(getIndexExpression());
  }

  /**
   * @return For {@code spam[x][y][n]} will return {@code spam} regardless number of its dimensions
   */
  @NotNull
  PyExpression getRootOperand();

  @NotNull
  PyExpression getOperand();

  @Nullable
  PyExpression getIndexExpression();
}
