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
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PsiCached;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.types.PyType;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 30.05.2005
 * Time: 0:33:13
 * To change this template use File | Settings | File Templates.
 */
public class PyKeywordArgumentImpl extends PyElementImpl implements PyKeywordArgument {
  public PyKeywordArgumentImpl(ASTNode astNode) {
    super(astNode);
  }

  @Nullable
  @PsiCached
  public String getKeyword() {
    ASTNode node = getKeywordNode();
    return node != null ? node.getText() : null;
  }

  @PsiCached
  public ASTNode getKeywordNode() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @PsiCached
  public PyExpression getValueExpression() {
    return PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ": " + getKeyword();
  }

  @Nullable
  protected Class<? extends PsiElement> getValidChildClass() {
    return PyExpression.class;
  }

  public PyType getType() {
    return null;
  }
}
