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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.PyElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.jetbrains.python.PyTokenTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 10:19:01
 * To change this template use File | Settings | File Templates.
 */
public class PyReferenceExpressionImpl extends PyElementImpl implements PyReferenceExpression {
  public PyReferenceExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PsiElement getElement() {
    return this;
  }

  @NotNull
  public PsiReference[] getReferences() {
    List<PsiReference> refs = new ArrayList<PsiReference>(Arrays.asList(super.getReferences()));
    refs.add(this);
    return refs.toArray(new PsiReference[refs.size()]);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyReferenceExpression(this);
  }

  @PsiCached
  public
  @Nullable
  PyExpression getQualifier() {
    final ASTNode[] nodes = getNode().getChildren(PyElementTypes.EXPRESSIONS);
    return (PyExpression)(nodes.length == 1 ? nodes[0].getPsi() : null);
  }

  public TextRange getRangeInElement() {
    final ASTNode nameElement = getNameElement();
    final int startOffset = nameElement != null ? nameElement.getStartOffset() : getNode().getTextRange().getEndOffset();
    return new TextRange(startOffset - getNode().getStartOffset(), getTextLength());
  }

  @PsiCached
  public
  @Nullable
  String getReferencedName() {
    final ASTNode nameElement = getNameElement();
    return nameElement != null ? nameElement.getText() : null;
  }

  @PsiCached
  private
  @Nullable
  ASTNode getNameElement() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  public
  @Nullable
  PsiElement resolve() {
    final String referencedName = getReferencedName();
    if (referencedName == null) return null;

    final PyExpression qualifier = getQualifier();
    if (qualifier != null) {
      if (qualifier instanceof PyCallExpression) {
        final PyCallExpression callExpression = (PyCallExpression)qualifier;
        final PyReferenceExpression expression = callExpression.getCalledFunctionReference();
        final PsiElement element = expression.resolve();
        if (element != null) {
          return PyResolveUtil.treeWalkUp(new PyResolveUtil.ResolveProcessor(referencedName), element, element, element);
        }
      }
      else if (qualifier instanceof PyReferenceExpression) {
        final PsiElement element = ((PyReferenceExpression)qualifier).resolve();
        if (element != null) {
          final PsiElement parent = element.getParent();
          if (parent instanceof PyAssignmentStatement) {
            final PyExpression value = ((PyAssignmentStatement)parent).getAssignedValue();
            if (value instanceof PyCallExpression) {
              final PsiElement c = ((PyCallExpression)value).getCalledFunctionReference().resolve();
              return PyResolveUtil.treeWalkUp(new PyResolveUtil.ResolveProcessor(referencedName), c, c, c);
            }
          }
        }
      }
      return null; // TODO?
    }

    if (getParent() instanceof PyImportElement) {
      final PsiFile[] files = FilenameIndex.getFilesByName(getProject(), referencedName + ".py", GlobalSearchScope.allScope(getProject()));
      if (files.length == 1) return files[0];
    }

    return PyResolveUtil.treeWalkUp(new PyResolveUtil.ResolveProcessor(referencedName), this, this, null);
  }

  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    final String referencedName = getReferencedName();
    if (referencedName == null) return ResolveResult.EMPTY_ARRAY;

    if (getQualifier() != null) {
      return ResolveResult.EMPTY_ARRAY; // TODO?
    }

    PyResolveUtil.MultiResolveProcessor processor = new PyResolveUtil.MultiResolveProcessor(referencedName);
    PyResolveUtil.treeWalkUp(processor, this, this, this);
    return processor.getResults();
  }

  public String getCanonicalText() {
    return null;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    ASTNode nameElement = getNameElement();
    if (nameElement != null) {
      final ASTNode newNameElement = getLanguage().getElementGenerator().createNameIdentifier(getProject(), newElementName);
      getNode().replaceChild(nameElement, newNameElement);
    }
    return this;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PsiNamedElement) {
      if (Comparing.equal(getReferencedName(), ((PsiNamedElement)element).getName())) {
        return resolve() == element;
      }
    }
    return false;
  }

  public Object[] getVariants() {
    if (getQualifier() != null) {
      return new Object[0]; // TODO?
    }

    final PyResolveUtil.VariantsProcessor processor = new PyResolveUtil.VariantsProcessor();
    PyResolveUtil.treeWalkUp(processor, this, this, this);
    return processor.getResult();
  }

  public boolean isSoft() {
    return false;
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    // in statements, process only the section in which the original expression was located
    PsiElement parent = getParent();
    if (parent instanceof PyStatement && lastParent == null && PsiTreeUtil.isAncestor(parent, place, true)) {
      return true;
    }

    // never resolve to references within the same assignment statement
    if (getParent() instanceof PyAssignmentStatement) {
      PsiElement placeParent = place.getParent();
      while (placeParent != null && placeParent instanceof PyExpression) {
        placeParent = placeParent.getParent();
      }
      if (placeParent == getParent()) {
        return true;
      }
    }

    if (this == place) {
      return true;
    }
    return processor.execute(this, substitutor);
  }

  public boolean shouldHighlightIfUnresolved() {
    return false;
  }

  public
  @Nullable
  String getUnresolvedDescription() {
    return null;
  }

  @Override
  public String toString() {
    return "PyReferenceExpression: " + getReferencedName();
  }
}
