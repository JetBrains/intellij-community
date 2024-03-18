// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.rename;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;


public final class RenamePyFunctionProcessor extends RenamePyElementProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof PyFunction;
  }

  @Override
  public boolean isToSearchInComments(@NotNull PsiElement element) {
    return PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION;
  }

  @Override
  public void setToSearchInComments(@NotNull PsiElement element, boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION = enabled;
  }

  @Override
  public boolean isToSearchForTextOccurrences(@NotNull PsiElement element) {
    return PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_FUNCTION;
  }

  @Override
  public void setToSearchForTextOccurrences(@NotNull PsiElement element, boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_FUNCTION = enabled;
  }

  @Override
  public PsiElement substituteElementToRename(@NotNull PsiElement element, @Nullable Editor editor) {
    final TypeEvalContext context = TypeEvalContext.codeInsightFallback(element.getProject());
    final PyFunction function = ObjectUtils.notNull(PyiUtil.getImplementation((PyFunction)element, context), (PyFunction)element);

    final PyClass containingClass = function.getContainingClass();
    if (containingClass == null) {
      return function;
    }
    if (PyUtil.isInitMethod(function)) {
      return containingClass;
    }

    final PyFunction deepestSuperMethod = PySuperMethodsSearch.findDeepestSuperMethod(function);
    if (!deepestSuperMethod.equals(function)) {
      final String message = PyBundle.message("python.rename.processor.override.message",
                                              function.getName(),
                                              containingClass.getQualifiedName(),
                                              deepestSuperMethod.getContainingClass().getQualifiedName());
      final int rc =
        Messages.showYesNoCancelDialog(element.getProject(), message, PyBundle.message("refactoring.rename"), Messages.getQuestionIcon());
      return switch (rc) {
        case Messages.YES -> deepestSuperMethod;
        case Messages.NO -> function;
        default -> null;
      };
    }

    final Property property = containingClass.findPropertyByCallable(function);
    if (property != null) {
      final PyTargetExpression site = property.getDefinitionSite();
      if (site != null) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          return site;
        }
        final int rc =
          Messages.showYesNoCancelDialog(element.getProject(),
                                         PyBundle.message("python.rename.processor.property", property.getName(), function.getName()), PyBundle.message("refactoring.rename"), Messages.getQuestionIcon());
        return switch (rc) {
          case Messages.YES -> site;
          case Messages.NO -> function;
          default -> null;
        };
      }
    }

    return function;
  }

  @Override
  public void prepareRenaming(@NotNull PsiElement element, @NotNull String newName, @NotNull Map<PsiElement, String> allRenames) {
    final PyFunction function = (PyFunction)element;

    PyOverridingMethodsSearch
      .search(function, true)
      .forEach(
        f -> {
          allRenames.put(f, newName);
          return true;
        }
      );

    PyiUtil
      .getOverloads(function, TypeEvalContext.codeInsightFallback(element.getProject()))
      .forEach(overload -> allRenames.put(overload, newName));

    final PsiElement originalElement = PyiUtil.getOriginalElement(function);
    if (originalElement != null) {
      allRenames.put(originalElement, newName);
    }

    final PsiElement stubElement = PyiUtil.getPythonStub(function);
    if (stubElement != null) {
      allRenames.put(stubElement, newName);
    }

    final PyClass containingClass = function.getContainingClass();
    if (containingClass != null) {
      final Property property = containingClass.findPropertyByCallable(function);
      if (property != null) {
        addRename(allRenames, newName, property.getGetter());
        addRename(allRenames, newName, property.getSetter());
        addRename(allRenames, newName, property.getDeleter());
      }
    }
  }

  private static void addRename(@NotNull Map<PsiElement, String> renames, @NotNull String newName, @NotNull Maybe<PyCallable> accessor) {
    final PyCallable callable = accessor.valueOrNull();
    if (callable instanceof PyFunction) {
      renames.put(callable, newName);
    }
  }
}
