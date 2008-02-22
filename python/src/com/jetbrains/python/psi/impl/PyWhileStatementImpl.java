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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyWhileStatement;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 15:47:44
 * To change this template use File | Settings | File Templates.
 */
public class PyWhileStatementImpl extends PyElementImpl implements PyWhileStatement {
    public PyWhileStatementImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyWhileStatement(this);
    }

    public @NotNull PyStatementList getStatementList() {
        return childToPsiNotNull(PyElementTypes.STATEMENT_LISTS, 0);
    }

    public @Nullable PyStatementList getElseStatementList() {
        return childToPsi(PyElementTypes.STATEMENT_LISTS, 1);
    }

    @Override public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                                 @NotNull ResolveState substitutor,
                                                 PsiElement lastParent,
                                                 @NotNull PsiElement place)
    {
        if (lastParent != null) {
            return true;
        }

        if (!getStatementList().processDeclarations(processor, substitutor, null, place)) {
            return false;
        }
        PyStatementList elseList = getElseStatementList();
        if (elseList != null) {
            return elseList.processDeclarations(processor, substitutor, null, place);
        }
        return true;
    }
}
