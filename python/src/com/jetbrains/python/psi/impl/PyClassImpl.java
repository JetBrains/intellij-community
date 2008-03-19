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
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
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
import java.util.ArrayList;
import java.util.List;

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

  @NotNull
  public PyStatementList getStatementList() {
    return childToPsiNotNull(PyElementTypes.STATEMENT_LIST);
  }

  @Nullable
  public PyExpression[] getSuperClassExpressions() {
    final PyParenthesizedExpression superExpression = PsiTreeUtil.getChildOfType(this, PyParenthesizedExpression.class);
    if (superExpression != null) {
      PyExpression expr = superExpression.getContainedExpression();
      if (expr instanceof PyTupleExpression) {
        return ((PyTupleExpression) expr).getElements();
      }
      if (expr != null) {
        return new PyExpression[] { expr };
      }
    }
    return null;
  }

  public PsiElement[] getSuperClassElements() {
    final PyExpression[] superExpressions = getSuperClassExpressions();
    if (superExpressions != null) {
      List<PsiElement> superClasses = new ArrayList<PsiElement>();
      for(PyExpression expr: superExpressions) {
        if (expr instanceof PyReferenceExpression && !"object".equals(expr.getText())) {
          PyReferenceExpression ref = (PyReferenceExpression) expr;
          final PsiElement result = ref.resolve();
          if (result != null) {
            superClasses.add(result);
          }
        }
      }
      return superClasses.toArray(new PsiElement[superClasses.size()]);
    }
    return null;
  }

  public PyClass[] getSuperClasses() {
    PsiElement[] superClassElements = getSuperClassElements();
    if (superClassElements != null) {
      List<PyClass> result = new ArrayList<PyClass>();
      for(PsiElement element: superClassElements) {
        if (element instanceof PyClass) {
          result.add((PyClass) element);
        }
      }
      return result.toArray(new PyClass[result.size()]);
    }
    return null;
  }

  @NotNull
  public PyFunction[] getMethods() {
    List<PyFunction> result = new ArrayList<PyFunction>();
    final PyStatementList statementList = getStatementList();
    for (PsiElement element : statementList.getChildren()) {
      if (element instanceof PyFunction) {
        result.add((PyFunction) element);
      }
    }
    return result.toArray(new PyFunction[result.size()]);
  }

  public PyFunction findMethodByName(@NotNull final String name) {
    PyFunction[] methods = getMethods();
    for(PyFunction method: methods) {
      if (name.equals(method.getName())) {
        return method;
      }
    }
    return null;
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    final PyStatementList statementList = getStatementList();
    for(PyFunction func: getMethods()) {
      if (!processor.execute(func, substitutor)) return false;
      if ("__init__".equals(func.getName())) {
        if (!processFields(processor, func, substitutor)) return false;
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
    final Ref<Boolean> result = new Ref<Boolean>();
    function.getStatementList().accept(new PyRecursiveElementVisitor() {
      public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
        if (!result.isNull()) return;
        super.visitPyAssignmentStatement(node);
        final PyExpression[] targets = node.getTargets();
        for(PyExpression target: targets) {
          if (target instanceof PyTargetExpression) {
            PyExpression qualifier = ((PyTargetExpression) target).getQualifier();
            if (qualifier != null && qualifier.getText().equals(params [0].getName())) {
              if (!processor.execute(target, substitutor)) {
                result.set(Boolean.FALSE);
              }
            }
          }
        }
      }
    });

    return result.isNull();
  }

  public int getTextOffset() {
    final ASTNode name = findNameIdentifier();
    return name != null ? name.getStartOffset() : super.getTextOffset();
  }

  public String getDocString() {
    return DocStringAnnotator.findDocString(getStatementList());
  }
}
