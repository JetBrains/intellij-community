/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author yole
 */
public class PyGlobalStatementImpl extends PyElementImpl implements PyGlobalStatement {
  private static final TokenSet TARGET_EXPRESSION_SET = TokenSet.create(PyElementTypes.TARGET_EXPRESSION);

  public PyGlobalStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyGlobalStatement(this);
  }

  @NotNull
  public PyTargetExpression[] getGlobals() {
    return childrenToPsi(TARGET_EXPRESSION_SET, PyTargetExpression.EMPTY_ARRAY);
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    return Arrays.<PyElement>asList(getGlobals());
  }

  public PyElement getElementNamed(final String the_name) {
    return IterHelper.findName(iterateNames(), the_name);
  }

  public boolean mustResolveOutside() {
    return true;
  }

  public void addGlobal(final String name) {
    final PyElementGenerator pyElementGenerator = PyElementGenerator.getInstance(getProject());
    add(pyElementGenerator.createComma().getPsi());
    add(pyElementGenerator.createFromText(LanguageLevel.getDefault(), PyGlobalStatement.class, "global " + name).getGlobals()[0]);
  }
}
