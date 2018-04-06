// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.rename;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
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

/**
 * @author yole
 */
public class RenamePyFunctionProcessor extends RenamePyElementProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof PyFunction;
  }

  @Override
  public boolean forcesShowPreview() {
    return true;
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
    final PyFunction function = toImplementationOtherwiseAsIs((PyFunction)element);

    final PyClass containingClass = function.getContainingClass();
    if (containingClass == null) {
      return function;
    }
    if (PyNames.INIT.equals(function.getName())) {
      return containingClass;
    }

    final PyFunction deepestSuperMethod = PySuperMethodsSearch.findDeepestSuperMethod(function);
    if (!deepestSuperMethod.equals(function)) {
      final String message = "Method " + function.getName() + " of class " + containingClass.getQualifiedName() + "\n" +
                             "overrides method of class " + deepestSuperMethod.getContainingClass().getQualifiedName() + ".\n" +
                             "Do you want to rename the base method?";
      final int rc = Messages.showYesNoCancelDialog(element.getProject(), message, "Rename", Messages.getQuestionIcon());
      switch (rc) {
        case Messages.YES:
          return deepestSuperMethod;
        case Messages.NO:
          return function;
        default:
          return null;
      }
    }

    final Property property = containingClass.findPropertyByCallable(function);
    if (property != null) {
      final PyTargetExpression site = property.getDefinitionSite();
      if (site != null) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          return site;
        }
        final String message = String.format("Do you want to rename the property '%s' instead of its accessor function '%s'?",
                                             property.getName(), function.getName());
        final int rc = Messages.showYesNoCancelDialog(element.getProject(), message, "Rename", Messages.getQuestionIcon());
        switch (rc) {
          case Messages.YES:
            return site;
          case Messages.NO:
            return function;
          default:
            return null;
        }
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

  @NotNull
  private static PyFunction toImplementationOtherwiseAsIs(@NotNull PyFunction function) {
    final PyFunction implementation = PyiUtil.getImplementation(function);
    return implementation != null ? implementation : function;
  }

  private static void addRename(@NotNull Map<PsiElement, String> renames, @NotNull String newName, @NotNull Maybe<PyCallable> accessor) {
    final PyCallable callable = accessor.valueOrNull();
    if (callable instanceof PyFunction) {
      renames.put(callable, newName);
    }
  }
}
