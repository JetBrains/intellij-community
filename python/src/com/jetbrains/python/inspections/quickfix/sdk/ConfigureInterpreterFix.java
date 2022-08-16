// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix.sdk;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.sdk.PySdkPopupFactory;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.PythonHelper.guessModule;

public class ConfigureInterpreterFix implements LocalQuickFix {

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return PyPsiBundle.message("INSP.interpreter.configure.python.interpreter");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (element == null) return;

    final Module module = guessModule(element);
    if (module == null) return;

    PySdkPopupFactory.Companion.createAndShow(project, module);
  }
}