/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 05.06.2005
 * Time: 10:38:44
 * To change this template use File | Settings | File Templates.
 */
public class PyAugAssignmentStatementImpl extends PyElementImpl implements PyAugAssignmentStatement {
  public PyAugAssignmentStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyAugAssignmentStatement(this);
  }

  @NotNull
  public PyExpression getTarget() {
    PyExpression target = childToPsi(PyElementTypes.EXPRESSIONS, 0);
    if (target == null) {
      throw new RuntimeException("Target missing in augmented assignment statement");
    }
    return target;
  }
}
