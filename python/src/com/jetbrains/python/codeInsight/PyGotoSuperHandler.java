// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexey.Ivanov
 */
public class PyGotoSuperHandler implements CodeInsightActionHandler {

  @Override
  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
    if (pyClass != null) {
      PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class, false, PyClass.class);
      if (function != null) {
        gotoSuperFunctions(editor, function, pyClass);
      }
      else {
        final PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class, false, PyClass.class);
        if (assignment != null && assignment.getTargets()[0] instanceof PyTargetExpression) {
          gotoSuperClassAttributes(editor, (PyTargetExpression) assignment.getTargets()[0], pyClass);
        }
        else {
          final TypeEvalContext context = TypeEvalContext.codeAnalysis(project, file);
          navigateOrChoose(editor, pyClass.getAncestorClasses(context), "Choose superclass");
        }
      }
    }
  }

  private static void gotoSuperFunctions(Editor editor, PyFunction function, PyClass pyClass) {
    final Collection<PyFunction> superFunctions = getAllSuperMethodsByName(function, pyClass);
    navigateOrChoose(editor, superFunctions, CodeInsightBundle.message("goto.super.method.chooser.title"));
  }

  private static void gotoSuperClassAttributes(Editor editor, PyTargetExpression attr, PyClass pyClass) {
    final Collection<PyTargetExpression> attrs = getAllSuperAttributesByName(attr, pyClass);
    navigateOrChoose(editor, attrs, "Choose superclass attribute");
  }

  private static void navigateOrChoose(Editor editor, Collection<? extends NavigatablePsiElement> superElements, final String title) {
    if (!superElements.isEmpty()) {
      NavigatablePsiElement[] superElementArray = superElements.toArray(new NavigatablePsiElement[superElements.size()]);
      if (superElementArray.length == 1) {
        superElementArray[0].navigate(true);
      }
      else {
        NavigationUtil.getPsiElementPopup(superElementArray, title).showInBestPositionFor(editor);
      }
    }
  }

  private static Collection<PyTargetExpression> getAllSuperAttributesByName(@NotNull final PyTargetExpression classAttr, PyClass pyClass) {
    final String name = classAttr.getName();
    if (name == null) {
      return Collections.emptyList();
    }
    final List<PyTargetExpression> result = new ArrayList<>();
    for (PyClass aClass: pyClass.getAncestorClasses(null)) {
      final PyTargetExpression superAttr = aClass.findClassAttribute(name, false, null);
      if (superAttr != null) {
        result.add(superAttr);
      }
    }
    return result;
  }

  private static Collection<PyFunction> getAllSuperMethodsByName(@NotNull final PyFunction method, PyClass pyClass) {
    final String name = method.getName();
    if (name == null) {
      return Collections.emptyList();
    }
    final List<PyFunction> result = new ArrayList<>();
    for (PyClass aClass: pyClass.getAncestorClasses(null)) {
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
