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
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.06.2005
 * Time: 21:48:14
 * To change this template use File | Settings | File Templates.
 */
public class PyImportStatementImpl extends PyElementImpl implements PyImportStatement {
    public PyImportStatementImpl(ASTNode astNode) {
        super(astNode);
    }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor, @NotNull final ResolveState state, final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    return getImportElement().processDeclarations(processor, state, lastParent, place);
  }

  public PyImportElement getImportElement() {
    return (PyImportElement)getNode().findChildByType(PyElementTypes.IMPORT_ELEMENT).getPsi();
  }
}
