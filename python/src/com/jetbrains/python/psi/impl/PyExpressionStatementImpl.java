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
import com.jetbrains.python.psi.PyElementVisitor;
import org.jetbrains.annotations.NotNull;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 12:15:43
 * To change this template use File | Settings | File Templates.
 */
public class PyExpressionStatementImpl extends PyElementImpl implements PyExpressionStatement {
    public PyExpressionStatementImpl(ASTNode astNode) {
        super(astNode);
    }

    public @NotNull
    PyExpression getExpression() {
        return childToPsiNotNull(PyElementTypes.EXPRESSIONS, 0);
    }

    @Override protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyExpressionStatement(this);
    }
}
