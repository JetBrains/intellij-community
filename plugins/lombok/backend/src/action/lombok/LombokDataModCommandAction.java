// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lombok.backend.action.lombok;

import com.intellij.lombok.shared.LombokBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiClass;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.jetbrains.annotations.NotNull;

import static de.plushnikov.intellij.plugin.util.PsiAnnotationUtil.addAnnotation;

final class LombokDataModCommandAction extends BaseLombokModCommandAction {
  LombokDataModCommandAction(@NotNull PsiClass element) {
    super(element);
  }

  @Override
  public @NotNull String getFamilyName() {
    return LombokBundle.message("action.defaultLombokData.text");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiClass psiClass, @NotNull ModPsiUpdater updater) {
    LombokGetterModCommandAction.processClass(psiClass);
    LombokSetterModCommandAction.processClass(psiClass);
    LombokToStringModCommandAction.processClass(psiClass);
    LombokEqualsAndHashcodeModCommandAction.processClass(psiClass);

    removeDefaultAnnotation(psiClass, LombokClassNames.GETTER);
    removeDefaultAnnotation(psiClass, LombokClassNames.SETTER);
    removeDefaultAnnotation(psiClass, LombokClassNames.TO_STRING);
    removeDefaultAnnotation(psiClass, LombokClassNames.EQUALS_AND_HASHCODE);

    addAnnotation(psiClass, LombokClassNames.DATA);

    final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(psiClass.getProject());
    javaCodeStyleManager.optimizeImports(psiClass.getContainingFile());
  }
}
