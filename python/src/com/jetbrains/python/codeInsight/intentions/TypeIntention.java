/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.documentation.doctest.PyDocstringFile;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * User: ktisha
 *
 * Common part for type specifying intentions
 */
public abstract class TypeIntention extends PyBaseIntentionAction {

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
  public static PyExpression getProblemElement(@Nullable PsiElement elementAt) {
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
  protected static PyNamedParameter getParameter(PyExpression problemElement, PsiElement resolved) {
    PyNamedParameter parameter = as(problemElement, PyNamedParameter.class);
    if (resolved instanceof PyNamedParameter) {
      parameter = (PyNamedParameter)resolved;
    }
    return parameter == null || parameter.isSelf() ? null : parameter;
  }

  private boolean isAvailableForReturn(@NotNull final PsiElement elementAt) {
    return findSuitableFunction(elementAt) != null;
  }

  @Nullable
  protected PyFunction findSuitableFunction(@NotNull PsiElement elementAt) {
    return findSuitableFunction(elementAt, input -> !isReturnTypeDefined(input));
  }

  @Nullable
  public static PyFunction findSuitableFunction(@NotNull PsiElement elementAt, @NotNull Predicate<PyFunction> extraCondition) {
    return ContainerUtil.getOnlyItem(findSuitableFunctions(elementAt, extraCondition));
  }

  @NotNull
  private static List<PyFunction> findSuitableFunctions(@NotNull PsiElement elementAt, @NotNull Predicate<PyFunction> extraCondition) {
    final StreamEx<PyFunction> definitions;
    final PyFunction underCaret = findFunctionDefinitionUnderCaret(elementAt);
    if (underCaret != null) {
      definitions = StreamEx.of(underCaret);
    }
    else {
      final PyCallExpression callExpression = getCallExpression(elementAt);
      if (callExpression == null) {
        definitions = StreamEx.empty();
      }
      else {
        definitions = StreamEx.of(callExpression.multiResolveCallee(getResolveContext(elementAt)))
                              .map(result -> result.getElement())
                              .select(PyFunction.class);
      }
    }
    final ProjectFileIndex index = ProjectFileIndex.getInstance(elementAt.getProject());
    return definitions.filter(elem -> !index.isInLibraryClasses(elem.getContainingFile().getVirtualFile()))
                      .filter(extraCondition)
                      .toList();
  }

  @Nullable
  private static PyFunction findFunctionDefinitionUnderCaret(@NotNull PsiElement elementAt) {
    final PyFunction parentFunction = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);
    if (parentFunction != null) {
      final ASTNode nameNode = parentFunction.getNameNode();
      if (nameNode != null) {
        final PsiElement prev = elementAt.getContainingFile().findElementAt(elementAt.getTextOffset()-1);
        if (nameNode.getPsi() == elementAt || nameNode.getPsi() == prev) {
          return parentFunction;
        }
      }
    }
    return null;
  }

  protected boolean isReturnTypeDefined(@NotNull PyFunction function) {
    return false;
  }

  @Nullable
  static PyCallExpression getCallExpression(PsiElement elementAt) {
    final PyExpression problemElement = getProblemElement(elementAt);
    if (problemElement != null) {
      PsiReference reference = problemElement.getReference();
      final PsiElement resolved = reference != null? reference.resolve() : null;
      if (resolved instanceof PyTargetExpression) {
        final PyResolveContext context = getResolveContext(elementAt);
        if (context.getTypeEvalContext().maySwitchToAST(resolved)) {
          final PyExpression assignedValue = ((PyTargetExpression)resolved).findAssignedValue();
          if (assignedValue instanceof PyCallExpression) {
            return (PyCallExpression)assignedValue;
          }
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

  protected static PyResolveContext getResolveContext(@NotNull PsiElement origin) {
    return PyResolveContext.noImplicits().withTypeEvalContext(TypeEvalContext.codeAnalysis(origin.getProject(), origin.getContainingFile()));
  }
}