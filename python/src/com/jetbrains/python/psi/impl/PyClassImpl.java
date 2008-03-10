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
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.validation.DocStringAnnotator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 03.06.2005
 * Time: 0:27:33
 * To change this template use File | Settings | File Templates.
 */
public class PyClassImpl extends PyPresentableElementImpl<PyClassStub> implements PyClass {
  public PyClassImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyClassImpl(final PyClassStub stub) {
    super(stub, PyElementTypes.CLASS_DECLARATION);
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    final ASTNode nameElement = getLanguage().getElementGenerator().createNameIdentifier(getProject(), name);
    getNode().replaceChild(findNameIdentifier(), nameElement);
    return this;
  }

  @Nullable
  @Override
  public String getName() {
    final PyClassStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    else {
      ASTNode node = findNameIdentifier();
      return node != null ? node.getText() : null;
    }
  }

  private ASTNode findNameIdentifier() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Override
  public Icon getIcon(int flags) {
    return Icons.CLASS_ICON;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyClass(this);
  }

  public
  @NotNull
  PyStatementList getStatementList() {
    return childToPsiNotNull(PyElementTypes.STATEMENT_LIST);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    final PyStatementList statementList = getStatementList();
    for (PsiElement element : statementList.getChildren()) {
      if (element instanceof PyFunction) {
        final PyFunction function = (PyFunction)element;
        final String name = function.getName();
        if ("__init__".equals(name)) {
          if (!processFields(processor, function, substitutor)) return false;
        }
        else {
          if (!processor.execute(element, substitutor)) return false;
        }
      }
    }
    for (PsiElement psiElement : statementList.getChildren()) {
      if (psiElement instanceof PyAssignmentStatement) {
        final PyAssignmentStatement assignmentStatement = (PyAssignmentStatement)psiElement;
        final PyExpression[] targets = assignmentStatement.getTargets();
        for (PyExpression target : targets) {
          if (target instanceof PyTargetExpression) {
            if (!processor.execute(target, substitutor)) return false;
          }
        }
      }
    }
    if (processor instanceof PyResolveUtil.VariantsProcessor) {
      return true;
    }
    return processor.execute(this, substitutor);
  }

  private static boolean processFields(final PsiScopeProcessor processor, final PyFunction function, final ResolveState substitutor) {
    final PyParameter[] params = function.getParameterList().getParameters();
    if (params.length == 0) return true;
    final PyStatement[] statements = function.getStatementList().getStatements();
    for(PyStatement stmt: statements) {
      if (stmt instanceof PyAssignmentStatement) {
        final PyExpression[] targets = ((PyAssignmentStatement)stmt).getTargets();
        for(PyExpression target: targets) {
          if (target instanceof PyTargetExpression) {
            PyExpression qualifier = ((PyTargetExpression) target).getQualifier();
            if (qualifier != null && qualifier.getText().equals(params [0].getName())) {
              if (!processor.execute(target, substitutor)) return false;
            }
          }
        }
      }
    }
    return true;
  }

  public int getTextOffset() {
    final ASTNode name = findNameIdentifier();
    return name != null ? name.getStartOffset() : super.getTextOffset();
  }

  public String getDocString() {
    return DocStringAnnotator.findDocString(getStatementList());
  }
}
