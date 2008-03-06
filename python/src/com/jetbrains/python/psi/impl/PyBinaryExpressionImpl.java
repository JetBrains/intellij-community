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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 11:17:07
 * To change this template use File | Settings | File Templates.
 */
public class PyBinaryExpressionImpl extends PyElementImpl implements PyBinaryExpression {

  public PyBinaryExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyBinaryExpression(this);
    }

    @PsiCached
    public PyExpression getLeftExpression() {
        return PsiTreeUtil.getChildOfType(this, PyExpression.class);
    }

    @PsiCached
    public PyExpression getRightExpression() {
        return PsiTreeUtil.getNextSiblingOfType(getLeftExpression(),
                PyExpression.class);
    }

    @PsiCached
    public PyElementType getOperator() {
        return (PyElementType)getNode().findChildByType(PyElementTypes.BINARY_OPS).getElementType();
    }

    @PsiCached
    public boolean isOperator(String chars) {
        ASTNode child = getNode().getFirstChildNode();
        StringBuffer buf = new StringBuffer();
        while (child != null) {
            IElementType elType = child.getElementType();
            if (elType instanceof PyElementType && PyElementTypes.BINARY_OPS.contains(elType)) {
                buf.append(child.getText());
            }
            child = child.getTreeNext();
        }
        return buf.toString().equals(chars);
    }

  public PyExpression getOppositeExpression(PyExpression expression)
      throws IllegalArgumentException {
    PyExpression right = getRightExpression();
    PyExpression left = getLeftExpression();
    if (expression.equals(left)) {
      return right;
    }
    if (expression.equals(right)) {
      return left;
    }
    throw new IllegalArgumentException("expression " + expression
        + " is neither left exp or right exp");
  }

    protected void deletePyChild(PyBaseElementImpl element)
            throws IncorrectOperationException {
      PyExpression left = getLeftExpression();
      PyExpression right = getRightExpression();
      if (left == element) {
        replace(right);
      } else if (right == element) {
        replace(left);
      } else {
        throw new IncorrectOperationException("Element " + element
            + " is neither left expression or right expression");
      }
    }

    protected @Nullable Class<? extends PsiElement> getValidChildClass() {
        return PyElement.class;
    }
}
