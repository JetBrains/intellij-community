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
package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * A pack of constraints that limit behavior of {@link com.jetbrains.python.psi.types.TypeEvalContext}.
 * Any two  {@link com.jetbrains.python.psi.types.TypeEvalContext}s may share their cache if their constraints are equal and no PSI changes
 * happened between their creation.
 * <p/>
 * This class created to support hash/equals for context.
 *
 * @author Ilya.Kazakevich
 */
class TypeEvalConstraints {
  final boolean myAllowDataFlow;
  final boolean myAllowStubToAST;
  final boolean myAllowCallContext;
  @Nullable final PsiFile myOrigin;

  /**
   * @see com.jetbrains.python.psi.types.TypeEvalContext
   */
  TypeEvalConstraints(final boolean allowDataFlow, final boolean allowStubToAST, final boolean allowCallContext,
                      @Nullable final PsiFile origin) {
    myAllowDataFlow = allowDataFlow;
    myAllowStubToAST = allowStubToAST;
    myAllowCallContext = allowCallContext;
    myOrigin = origin;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TypeEvalConstraints)) return false;

    TypeEvalConstraints that = (TypeEvalConstraints)o;

    if (myAllowDataFlow != that.myAllowDataFlow) return false;
    if (myAllowStubToAST != that.myAllowStubToAST) return false;
    if (myAllowCallContext != that.myAllowCallContext) return false;
    if (myOrigin != null ? !myOrigin.equals(that.myOrigin) : that.myOrigin != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (myAllowDataFlow ? 1 : 0);
    result = 31 * result + (myAllowStubToAST ? 1 : 0);
    result = 31 * result + (myOrigin != null ? myOrigin.hashCode() : 0);
    result = 31 * result + (myAllowCallContext ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return String.format("TypeEvalConstraints(%b, %b, %b, %s)", myAllowDataFlow, myAllowStubToAST, myAllowCallContext, myOrigin);
  }
}
