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

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * A statement part that has a condition before it.
 */
@ApiStatus.Experimental
public interface PyAstConditionalStatementPart extends PyAstStatementPart {
  /**
   * @return the condition expression.
   */
  @Nullable
  default PyAstExpression getCondition() {
    ASTNode n = getNode().findChildByType(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens());
    if (n != null) {
      return (PyAstExpression)n.getPsi();
    }
    return null;
  }
}
