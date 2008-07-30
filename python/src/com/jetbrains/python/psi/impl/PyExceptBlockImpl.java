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
import org.jetbrains.annotations.Nullable;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.06.2005
 * Time: 23:37:49
 * To change this template use File | Settings | File Templates.
 */
public class PyExceptBlockImpl extends PyElementImpl implements PyExceptBlock {
  public PyExceptBlockImpl(ASTNode astNode) {
      super(astNode);
  }

  @Override protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
      pyVisitor.visitPyExceptBlock(this);
  }

  public @Nullable PyExpression getExceptClass() {
      return childToPsi(PyElementTypes.EXPRESSIONS, 0);
  }

  public @Nullable PyExpression getTarget() {
      return childToPsi(PyElementTypes.EXPRESSIONS, 1);
  }

  public @NotNull PyStatementList getStatementList() {
      return childToPsiNotNull(PyElementTypes.STATEMENT_LIST);
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    return new SingleIterable<PyElement>(getTarget());
  }

  public PyElement getElementNamed(final String the_name) {
    PyElement target = getTarget();
    return ((target != null) && the_name.equals(target.getName()))? target : null;
  }

  public boolean mustResolveOutside() {
    return false; 
  }
}
