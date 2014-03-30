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
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.documentation.doctest.PyDocstringFile;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: ktisha
 *
 * Common part for type specifying intentions
 */
public abstract class TypeIntention implements IntentionAction {

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile) || file instanceof PyDocstringFile) return false;
    updateText(false);

    final PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    if (elementAt == null) return false;
    if (isAvailableForParameter(project, elementAt)) {
      return true;
    }
    if (isAvailableForReturn(elementAt)) {
      updateText(true);
      return true;
    }
    return false;
  }

  private boolean isAvailableForParameter(Project project, PsiElement elementAt) {
    final PyExpression problemElement = getProblemElement(elementAt);
    if (problemElement == null) return false;
    if (PsiTreeUtil.getParentOfType(problemElement, PyLambdaExpression.class) != null) {
      return false;
    }
    final PsiReference reference = problemElement.getReference();
    if (reference instanceof PsiPolyVariantReference) {
      final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
      if (results.length != 1) return false;
    }
    final VirtualFile virtualFile = problemElement.getContainingFile().getVirtualFile();
    if (virtualFile != null) {
      if (ProjectRootManager.getInstance(project).getFileIndex().isInLibraryClasses(virtualFile)) {
        return false;
      }
    }
    final PsiElement resolved = reference != null ? reference.resolve() : null;
    final PyParameter parameter = getParameter(problemElement, resolved);

    return parameter != null && !isParamTypeDefined(parameter);
  }

  @Nullable
  protected static PyExpression getProblemElement(@Nullable PsiElement elementAt) {
    PyExpression problemElement = PsiTreeUtil.getParentOfType(elementAt, PyNamedParameter.class, PyReferenceExpression.class);
    if (problemElement == null) return null;
    if (problemElement instanceof PyQualifiedExpression) {
      final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
      if (qualifier != null && !qualifier.getText().equals(PyNames.CANONICAL_SELF)) {
        problemElement = qualifier;
      }
    }
    return problemElement;
  }

  protected abstract void updateText(boolean isReturn);

  protected boolean isParamTypeDefined(PyParameter parameter) {
    return false;
  }

  @Nullable
  protected static PyParameter getParameter(PyExpression problemElement, PsiElement resolved) {
    PyParameter parameter = problemElement instanceof PyParameter? (PyParameter)problemElement : null;
    if (resolved instanceof PyParameter)
      parameter = (PyParameter)resolved;
    return parameter;
  }

  private boolean isAvailableForReturn(@NotNull final PsiElement elementAt) {
    final PyFunction parentFunction = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);
    if (parentFunction != null) {
      final ASTNode nameNode = parentFunction.getNameNode();
      if (nameNode != null) {
        final PsiElement prev = elementAt.getPrevSibling();
        if (nameNode.getPsi() == elementAt || nameNode.getPsi() == prev) {
          return !isReturnTypeDefined(parentFunction);
        }
      }
    }

    final PyCallExpression callExpression = getCallExpression(elementAt);
    if (callExpression == null) return false;
    final PyExpression callee = callExpression.getCallee();
    if (callee == null) return false;
    final PsiReference reference = callee.getReference();
    if (reference instanceof PsiPolyVariantReference) {
      final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
      if (results.length == 1) {
        final PsiElement result = results[0].getElement();
        if (!(result instanceof PyFunction)) return false;
        final PsiFile psiFile = result.getContainingFile();
        if (psiFile == null) return false;
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null) {
          if (ProjectRootManager.getInstance(psiFile.getProject()).getFileIndex().isInLibraryClasses(virtualFile)) {
            return false;
          }
        }
        return !isReturnTypeDefined((PyFunction)result);
      }
    }
    return false;
  }

  protected boolean isReturnTypeDefined(@NotNull PyFunction function) {
    return false;
  }

  @Nullable
  protected static PyCallExpression getCallExpression(PsiElement elementAt) {
    final PyExpression problemElement = getProblemElement(elementAt);
    if (problemElement != null) {
      PsiReference reference = problemElement.getReference();
      final PsiElement resolved = reference != null? reference.resolve() : null;
      if (resolved instanceof PyTargetExpression) {
        final PyExpression assignedValue = ((PyTargetExpression)resolved).findAssignedValue();
        if (assignedValue instanceof PyCallExpression) {
          return (PyCallExpression)assignedValue;
        }
      }
    }

    PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(elementAt, PyAssignmentStatement.class);
    if (assignmentStatement != null) {
      final PyExpression assignedValue = assignmentStatement.getAssignedValue();
      if (assignedValue instanceof PyCallExpression) {
        return (PyCallExpression)assignedValue;
      }
    }
    return PsiTreeUtil.getParentOfType(elementAt, PyCallExpression.class, false);
  }

  @Nullable
  protected Callable getCallable(PsiElement elementAt) {
    PyCallExpression callExpression = getCallExpression(elementAt);

    if (callExpression != null && elementAt != null) {
      final Callable callable = callExpression.resolveCalleeFunction(getResolveContext(elementAt));
      return callable == null ? PsiTreeUtil.getParentOfType(elementAt, PyFunction.class) : callable;
    }
    return PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);
  }

  protected PyResolveContext getResolveContext(@NotNull PsiElement origin) {
    return PyResolveContext.defaultContext().withTypeEvalContext(TypeEvalContext.codeAnalysis(origin.getContainingFile()));
  }

  public boolean startInWriteAction() {
    return true;
  }
}