// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.jetbrains.python.documentation.doctest.PyDoctestFile;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * User: ktisha
 * Common part for type specifying intentions
 */
@ApiStatus.Internal
public abstract class TypeIntention extends PyBaseIntentionAction {

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (!(psiFile instanceof PyFile) || psiFile instanceof PyDoctestFile) return false;
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
    return findOnlySuitableFunction(file, editor.getCaretModel().getOffset(), input -> !isReturnTypeDefined(input));
  }

  /**
   * Finds the function at {@code offset} of {@code file}: the function whose name is there, or the callee of the call there.
   * Returns null when there is no such function, or more than one, or when the function is in a library.
   */
  public static @Nullable PyFunction findOnlySuitableFunction(@NotNull PsiFile file, int offset, @NotNull Predicate<PyFunction> condition) {
    final PsiElement elementAt = getElementUnderCaret(file, offset);
    return elementAt != null ? ContainerUtil.getOnlyItem(findSuitableFunctions(elementAt, condition)) : null;
  }

  protected final @Nullable PyNamedParameter findOnlySuitableParameter(@NotNull Editor editor, @NotNull PsiFile file) {
    return findOnlySuitableParameter(file, editor.getCaretModel().getOffset(), this::isParamTypeDefined);
  }

  /**
   * Finds the parameter at {@code offset} of {@code file}, or the parameter that the reference there resolves to.
   * Returns null for {@code self}, a lambda parameter, a parameter in a library, and a parameter for which {@code isTypeDefined} holds.
   */
  static @Nullable PyNamedParameter findOnlySuitableParameter(@NotNull PsiFile file,
                                                              int offset,
                                                              @NotNull Predicate<PyNamedParameter> isTypeDefined) {
    final PsiElement elementAt = getElementUnderCaret(file, offset);
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
      .filter(param -> !isTypeDefined.test(param))
      .findFirst().orElse(null);
  }

  private static @Nullable PsiElement getElementUnderCaret(@NotNull PsiFile file, int caretOffset) {
    final int offset = TargetElementUtilBase.adjustOffset(file, file.getFileDocument(), caretOffset);
    return PyUtil.findNonWhitespaceAtOffset(file, offset);
  }

  protected abstract void updateText(boolean isReturn);

  protected abstract boolean isParamTypeDefined(@NotNull PyNamedParameter parameter);

  protected abstract boolean isReturnTypeDefined(@NotNull PyFunction function);

  private static @NotNull List<PyFunction> findSuitableFunctions(@NotNull PsiElement elementAt,
                                                                 @NotNull Predicate<PyFunction> extraCondition) {
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
        final PsiElement prev = elementAt.getContainingFile().findElementAt(elementAt.getTextOffset() - 1);
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
