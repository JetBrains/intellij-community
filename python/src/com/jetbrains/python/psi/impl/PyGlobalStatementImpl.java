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
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.PyReferenceExpression;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 05.06.2005
 * Time: 10:29:42
 * To change this template use File | Settings | File Templates.
 */
public class PyGlobalStatementImpl extends PyElementImpl implements PyGlobalStatement {
    private TokenSet REFERENCES = TokenSet.create(PyElementTypes.REFERENCE_EXPRESSION);

    public PyGlobalStatementImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyGlobalStatement(this);
    }

    @NotNull public PyReferenceExpression[] getGlobals() {
        return childrenToPsi(REFERENCES, PyReferenceExpression.EMPTY_ARRAY);
    }

    public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                       @NotNull ResolveState substitutor,
                                       PsiElement lastParent,
                                       @NotNull PsiElement place) {
        for (PyExpression expression: getGlobals()) {
            if (!expression.processDeclarations(processor, substitutor, lastParent, place)) {
                return false;
            }
        }
        return true;
    }
}
