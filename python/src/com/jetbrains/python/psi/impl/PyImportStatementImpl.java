/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayFactory;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.stubs.PyImportStatementStub;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyImportStatementImpl extends PyBaseElementImpl<PyImportStatementStub> implements PyImportStatement {
  public PyImportStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyImportStatementImpl(PyImportStatementStub stub) {
    this(stub, PyElementTypes.IMPORT_STATEMENT);
  }

  public PyImportStatementImpl(PyImportStatementStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @NotNull
  public PyImportElement[] getImportElements() {
    final PyImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.getChildrenByType(PyElementTypes.IMPORT_ELEMENT, new ArrayFactory<PyImportElement>() {
        @NotNull
        public PyImportElement[] create(int count) {
          return new PyImportElement[count];
        }
      });
    }
    return childrenToPsi(TokenSet.create(PyElementTypes.IMPORT_ELEMENT), new PyImportElement[0]);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyImportStatement(this);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    PyPsiUtils.deleteAdjacentComma(this, child, getImportElements());
    super.deleteChildInternal(child);
  }
}
