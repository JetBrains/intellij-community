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
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 11:51:03
 * To change this template use File | Settings | File Templates.
 */
public class PyParenthesizedExpressionImpl extends PyElementImpl implements PyParenthesizedExpression {
    public PyParenthesizedExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyParenthesizedExpression(this);
    }

    @SuppressWarnings({"ConstantConditions"})
    public PyExpression getContainedExpression() {
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof PyExpression) {
                return (PyExpression) child;
            }
        }
        throw new IllegalStateException("no contained expression");
    }
}
