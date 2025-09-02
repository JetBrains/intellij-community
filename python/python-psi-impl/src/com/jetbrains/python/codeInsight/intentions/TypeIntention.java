// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.documentation.doctest.PyDocstringFile;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * User: ktisha
 * Common part for type specifying intentions
 */
public abstract class TypeIntention extends PyBaseIntentionAction {

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (!(psiFile instanceof PyFile) || psiFile instanceof PyDocstringFile) return false;
    updateText(false);

    if (findOnlySuitableParameter(editor, psiFile) != null) {
      return true;
    }
    if (findOnlySuitableFunction(editor, psiFile) != null) {
      updateText(true);
      return true;
    }
    return false;
  }

  protected final @Nullable PyFunction findOnlySuitableFunction(@NotNull Editor editor, @NotNull PsiFile file) {
    return findOnlySuitableFunction(editor, file, input -> !isReturnTypeDefined(input));
  }

  public static @Nullable PyFunction findOnlySuitableFunction(@NotNull Editor editor, @NotNull PsiFile file, Predicate<PyFunction> condition) {
    final PsiElement elementAt = getElementUnderCaret(editor, file);
    return elementAt != null ? ContainerUtil.getOnlyItem(findSuitableFunctions(elementAt, condition)) : null;
  }

  protected final @Nullable PyNamedParameter findOnlySuitableParameter(@NotNull Editor editor, @NotNull PsiFile file) {
    final PsiElement elementAt = getElementUnderCaret(editor, file);
    final StreamEx<PyNamedParameter> parameters;
    final PyNamedParameter immediateParam = PsiTreeUtil.getParentOfType(elementAt, PyNamedParameter.class);
    if (immediateParam != null) {
      parameters = StreamEx.of(immediateParam);
    }
    else {
      final PyReferenceExpression referenceExpr = PsiTreeUtil.getParentOfType(elementAt, PyReferenceExpression.class);
      if (referenceExpr != null) {
        parameters = StreamEx.of(PyUtil.multiResolveTopPriority(referenceExpr, getResolveContext(elementAt)))
                             .select(PyNamedParameter.class);
      }
      else {
        parameters = StreamEx.empty();
      }
    }
    final ProjectFileIndex index = ProjectFileIndex.getInstance(file.getProject());
    return parameters
      .filter(param -> !param.isSelf())
      .filter(param -> PsiTreeUtil.getParentOfType(param, PyLambdaExpression.class) == null)
      .filter(param -> {
        VirtualFile dir = param.getContainingFile().getOriginalFile().getVirtualFile();
        return dir != null && !index.isInLibraryClasses(dir);
      })
      .filter(param -> !isParamTypeDefined(param))
      .findFirst().orElse(null);
  }

  private static @Nullable PsiElement getElementUnderCaret(@NotNull Editor editor, @NotNull PsiFile file) {
    final int offset = TargetElementUtilBase.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
    return PyUtil.findNonWhitespaceAtOffset(file, offset);
  }

  protected abstract void updateText(boolean isReturn);

  protected abstract boolean isParamTypeDefined(@NotNull PyNamedParameter parameter);

  protected abstract boolean isReturnTypeDefined(@NotNull PyFunction function);

  private static @NotNull List<PyFunction> findSuitableFunctions(@NotNull PsiElement elementAt, @NotNull Predicate<PyFunction> extraCondition) {
    final StreamEx<PyFunction> definitions;
    final PyFunction immediateDefinition = findFunctionDefinitionUnderCaret(elementAt);
    if (immediateDefinition != null) {
      definitions = StreamEx.of(immediateDefinition);
    }
    else {
      definitions = StreamEx.of(getCallExpressions(elementAt))
                            .flatMap(call -> StreamEx.of(call.multiResolveCallee(getResolveContext(elementAt))))
                            .map(result -> result.getCallable())
                            .select(PyFunction.class);
    }
    final ProjectFileIndex index = ProjectFileIndex.getInstance(elementAt.getProject());
    return definitions
      .filter(elem -> {
        VirtualFile dir = elem.getContainingFile().getOriginalFile().getVirtualFile();
        return dir != null && !index.isInLibraryClasses(dir);
      })
      .filter(extraCondition)
      .toList();
  }

  private static @Nullable PyFunction findFunctionDefinitionUnderCaret(@NotNull PsiElement elementAt) {
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

  private static @NotNull List<PyCallExpression> getCallExpressions(@NotNull PsiElement elementAt) {
    final PyResolveContext context = getResolveContext(elementAt);
    final PyReferenceExpression referenceExpr = PsiTreeUtil.getParentOfType(elementAt, PyReferenceExpression.class);
    if (referenceExpr != null) {
      final List<PyCallExpression> calls = StreamEx.of(PyUtil.multiResolveTopPriority(referenceExpr, context))
                                                   .select(PyTargetExpression.class)
                                                   .filter(target -> context.getTypeEvalContext().maySwitchToAST(target))
                                                   .map(target -> target.findAssignedValue())
                                                   .select(PyCallExpression.class)
                                                   .toList();
      if (!calls.isEmpty()) {
        return calls;
      }
    }

    final PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(elementAt, PyAssignmentStatement.class);
    if (assignment != null) {
      final PyExpression assignedValue = assignment.getAssignedValue();
      if (assignedValue instanceof PyCallExpression) {
        return Collections.singletonList((PyCallExpression)assignedValue);
      }
    }
    final PyCallExpression immediateCall = PsiTreeUtil.getParentOfType(elementAt, PyCallExpression.class, false);
    if (immediateCall != null) {
      return Collections.singletonList(immediateCall);
    }
    return Collections.emptyList();
  }

  private static PyResolveContext getResolveContext(@NotNull PsiElement origin) {
    final TypeEvalContext typeEvalContext = TypeEvalContext.codeAnalysis(origin.getProject(), origin.getContainingFile());
    return PyResolveContext.defaultContext(typeEvalContext);
  }
}
