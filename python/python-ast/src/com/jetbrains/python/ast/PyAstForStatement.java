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
package com.jetbrains.python.ast;

import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.impl.PyUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.python.ast.PyAstElementKt.findNotNullChildByClass;

/**
 * The 'for/else' statement.
 */
@ApiStatus.Experimental
public interface PyAstForStatement extends PyAstLoopStatement, PyAstStatementWithElse, PyAstNamedElementContainer {
  @NotNull
  default PyAstForPart getForPart() {
    return findNotNullChildByClass(this, PyAstForPart.class);
  }

  @Override
  @NotNull
  default List<PsiNamedElement> getNamedElements() {
    PyAstExpression tgt = getForPart().getTarget();
    final List<PyAstExpression> expressions = PyUtilCore.flattenedParensAndStars(tgt);
    final List<PsiNamedElement> results = new ArrayList<>();
    for (PyAstExpression expression : expressions) {
      if (expression instanceof PsiNamedElement) {
        results.add((PsiNamedElement)expression);
      }
    }
    return results;
  }

  default boolean isAsync() {
    return getNode().findChildByType(PyTokenTypes.ASYNC_KEYWORD) != null;
  }
}
