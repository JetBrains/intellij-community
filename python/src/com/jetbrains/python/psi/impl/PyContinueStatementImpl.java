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
import com.jetbrains.python.psi.PyContinueStatement;
import com.jetbrains.python.psi.PyElementVisitor;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 31.05.2005
 * Time: 9:39:19
 * To change this template use File | Settings | File Templates.
 */
public class PyContinueStatementImpl extends PyElementImpl implements PyContinueStatement {
    public PyContinueStatementImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyContinueStatement(this);
    }
}
