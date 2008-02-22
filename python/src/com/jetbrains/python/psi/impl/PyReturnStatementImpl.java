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
import org.jetbrains.annotations.Nullable;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReturnStatement;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 23:26:12
 * To change this template use File | Settings | File Templates.
 */
public class PyReturnStatementImpl extends PyElementImpl implements PyReturnStatement {
    public PyReturnStatementImpl(ASTNode astNode) {
        super(astNode);
    }

    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyReturnStatement(this);
    }

    public @Nullable
    PyExpression getExpression() {
        return childToPsi(PyElementTypes.EXPRESSIONS, 0);
    }
}
