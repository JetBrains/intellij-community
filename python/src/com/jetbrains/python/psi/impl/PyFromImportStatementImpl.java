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
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.06.2005
 * Time: 22:31:35
 * To change this template use File | Settings | File Templates.
 */
public class PyFromImportStatementImpl extends PyElementImpl implements PyFromImportStatement {
  public PyFromImportStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyFromImportStatement(this);
  }

  public boolean isStarImport() {
    return getNode().findChildByType(PyTokenTypes.MULT) != null;
  }

  public PyImportElement[] getImportElements() {
    List<PyImportElement> result = new ArrayList<PyImportElement>();
    final ASTNode importKeyword = getNode().findChildByType(PyTokenTypes.IMPORT_KEYWORD);
    if (importKeyword != null) {
      for(ASTNode node = importKeyword.getTreeNext(); node != null; node = node.getTreeNext()) {
        if (node.getElementType() == PyElementTypes.IMPORT_ELEMENT) {
          result.add((PyImportElement) node.getPsi());
        }
      }
    }
    return result.toArray(new PyImportElement[result.size()]);
  }

  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor, @NotNull final ResolveState state, final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    PyImportElement[] importElements = getImportElements();
    for(PyImportElement element: importElements) {
      if (!element.processDeclarations(processor, state, lastParent, place)) {
        return false;
      }
    }
    return true;
  }
}
