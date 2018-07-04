// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.override.PyMethodMember;
import com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class PyImplementMethodsQuickFix extends LocalQuickFixOnPsiElement {

  @NotNull
  private final Collection<PyFunction> myToImplement;

  public PyImplementMethodsQuickFix(@NotNull PyClass cls, @NotNull Collection<PyFunction> toImplement) {
    super(cls);
    myToImplement = toImplement;
  }

  @NotNull
  @Override
  public String getText() {
    return PyBundle.message("QFIX.NAME.implement.methods");
  }

  @Override
  @NonNls
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    final Editor editor = PyQuickFixUtil.getEditor(file);

    if (editor != null && startElement instanceof PyClass) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        PyOverrideImplementUtil.overrideMethods(editor, (PyClass)startElement, ContainerUtil.map(myToImplement, PyMethodMember::new), true);
      }
      else {
        PyOverrideImplementUtil.chooseAndImplementMethods(project, editor, (PyClass)startElement, myToImplement);
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
