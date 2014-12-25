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
package com.jetbrains.python.psi.impl;

import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyConditionalStatementPart;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.intellij.lang.ASTNode;

/**
 * User: dcheryasov
 * Date: Mar 16, 2009 4:46:26 AM
 */
public abstract class PyConditionalStatementPartImpl extends PyStatementPartImpl implements PyConditionalStatementPart {
  public PyConditionalStatementPartImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyExpression getCondition() {
    ASTNode n = getNode().findChildByType(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens());
    if (n != null) {
      return (PyExpression)n.getPsi();
    }
    return null;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyConditionalStatementPart(this);
  }
}
