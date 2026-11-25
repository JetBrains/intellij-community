// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.Navigatable;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Finds the super classes, methods or class attributes in a background read action and navigates to them on the EDT.
 */
@ApiStatus.Internal
public final class PyGotoSuperHandler implements CodeInsightActionHandler {

  @Override
  public void invoke(final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile psiFile) {
    var offset = editor.getCaretModel().getOffset();
    var title = new Ref<@PopupTitle String>();
    new PsiTargetNavigator<>(() -> findSuperElements(psiFile, offset, title))
      .elementsConsumer((elements, navigator) -> navigator.title(title.get()))
      .navigate(editor, null, element -> {
        if (element instanceof Navigatable navigatable) {
          navigatable.navigate(true);
        }
        return true;
      });
  }

  private static @NotNull List<PsiElement> findSuperElements(@NotNull PsiFile psiFile, int offset, @NotNull Ref<@PopupTitle String> title) {
    var element = psiFile.findElementAt(offset);
    var pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
    if (pyClass == null) {
      return List.of();
    }
    var function = PsiTreeUtil.getParentOfType(element, PyFunction.class, false, PyClass.class);
    if (function != null) {
      title.set(CodeInsightBundle.message("goto.super.method.chooser.title"));
      return toNavigationTargets(getAllSuperMethodsByName(function, pyClass), function);
    }
    var assignment = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class, false, PyClass.class);
    if (assignment != null && assignment.getTargets()[0] instanceof PyTargetExpression target) {
      title.set(PyCodeInsightBundle.message("code.insight.goto.superclass.attribute.chooser.title"));
      return toNavigationTargets(getAllSuperAttributesByName(target, pyClass), target);
    }
    title.set(PyCodeInsightBundle.message("goto.superclass.choose"));
    var context = TypeEvalContext.codeAnalysis(psiFile.getProject(), psiFile);
    return toNavigationTargets(pyClass.getAncestorClasses(context), psiFile);
  }

  private static @NotNull List<PsiElement> toNavigationTargets(@NotNull Collection<? extends NavigatablePsiElement> superElements,
                                                               @NotNull PsiElement source) {
    return superElements.stream()
      .<PsiElement>map(element -> PyiUtil.getNavigationTarget(element, source) instanceof NavigatablePsiElement target ? target : element)
      .distinct()
      .toList();
  }

  private static Collection<PyTargetExpression> getAllSuperAttributesByName(final @NotNull PyTargetExpression classAttr, PyClass pyClass) {
    final String name = classAttr.getName();
    if (name == null) {
      return Collections.emptyList();
    }
    final List<PyTargetExpression> result = new ArrayList<>();
    for (PyClass aClass : pyClass.getAncestorClasses(null)) {
      final PyTargetExpression superAttr = aClass.findClassAttribute(name, false, null);
      if (superAttr != null) {
        result.add(superAttr);
      }
    }
    return result;
  }

  private static Collection<PyFunction> getAllSuperMethodsByName(final @NotNull PyFunction method, PyClass pyClass) {
    final String name = method.getName();
    if (name == null) {
      return Collections.emptyList();
    }
    final List<PyFunction> result = new ArrayList<>();
    for (PyClass aClass : pyClass.getAncestorClasses(null)) {
      final PyFunction byName = aClass.findMethodByName(name, false, null);
      if (byName != null) {
        result.add(byName);
      }
    }
    return result;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
