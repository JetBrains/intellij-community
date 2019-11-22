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

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyWithItem;

/**
 * @author yole
 */
public class PyWithItemImpl extends PyElementImpl implements PyWithItem {
  public PyWithItemImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyWithItem(this);
  }

  @Override
  public PyExpression getExpression() {
    ASTNode[] children = getNode().getChildren(null);
    for (ASTNode child: children) {
      final PsiElement e = child.getPsi();
      if (e instanceof PyExpression) {
        return (PyExpression)e;
      }
      else if (child.getElementType() == PyTokenTypes.AS_KEYWORD) {
        break;
      }
    }
    return null;
  }

  @Override
  public PyExpression getTarget() {
    ASTNode[] children = getNode().getChildren(null);
    boolean foundAs = false;
    for (ASTNode child : children) {
      if (child.getElementType() == PyTokenTypes.AS_KEYWORD) {
        foundAs = true;
      }
      else if (foundAs && child.getPsi() instanceof PyExpression) {
        return (PyExpression) child.getPsi();
      }
    }
    return null;
  }
}
