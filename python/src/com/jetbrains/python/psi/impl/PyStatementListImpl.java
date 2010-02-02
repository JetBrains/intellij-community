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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.PyStatementList;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 16:39:47
 * To change this template use File | Settings | File Templates.
 */
public class PyStatementListImpl extends PyElementImpl implements PyStatementList {
  public PyStatementListImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyStatementList(this);
  }

  @PsiCached
  public PyStatement[] getStatements() {
    return childrenToPsi(PyElementTypes.STATEMENTS, PyStatement.EMPTY_ARRAY);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    // in if statements, process only the section in which the original expression was located
    PsiElement parent = getParent();
    if (parent instanceof PyStatement && lastParent == null && PsiTreeUtil.isAncestor(parent, place, true)) {
      return true;
    }

    PyStatement[] statements = getStatements();
    if (lastParent != null) {
      // if we're processing the statement list in which the last parent is found, scan up
      // from parent
      for (int i = 0; i < statements.length; i++) {
        if (statements[i] == lastParent) {
          for (int j = i - 1; j >= 0; j--) {
            if (!statements[j].processDeclarations(processor, substitutor, lastParent, place)) {
              return false;
            }
          }
        }
      }
    }

    for (PyStatement statement : getStatements()) {
      if (!statement.processDeclarations(processor, substitutor, lastParent, place)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    final PsiElement[] elements = createElements2Add(element);
    if (elements != null){
      super.add(elements[1]);
      return super.add(elements[0]);
    }
    return super.add(element);
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    final PsiElement[] elements = createElements2Add(element);
    if (elements != null){
      return super.addBefore(elements[0], super.addBefore(elements[1], anchor));
    }
    return super.addBefore(element, anchor);
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    final PsiElement[] elements = createElements2Add(element);
    if (elements != null){
      return super.addAfter(elements[0], super.addAfter(elements[1], anchor));
    }
    return super.addAfter(element, anchor);
  }

  @Nullable
  /**
   * Indents given element and creates pair of indented psiElement and psiWhitespace(indent)
   */
  private PsiElement[] createElements2Add(final PsiElement element) {
    final PsiElement sibling = getPrevSibling();
    final String whitespace = sibling instanceof PsiWhiteSpace ? sibling.getText() : "";
    final int i = whitespace.lastIndexOf("\n");
    final int indent = i != -1 ? whitespace.length() - i : 0;
    try {
      final String newElementText = StringUtil.shiftIndentInside(element.getText(), indent, true);
      final PyFile dummyFile =
        (PyFile) PythonLanguage.getInstance().createDummyFile(getProject(), newElementText);
      return new PsiElement[]{dummyFile.getLastChild(), PsiTreeUtil.getDeepestFirst(dummyFile)};
    }
    catch (IOException e) {
      return null;
    }
  }
}
