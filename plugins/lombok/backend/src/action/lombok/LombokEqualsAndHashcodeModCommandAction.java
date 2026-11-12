// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lombok.backend.action.lombok;

import com.intellij.lombok.shared.LombokBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.jetbrains.annotations.NotNull;

import static de.plushnikov.intellij.plugin.util.PsiAnnotationUtil.addAnnotation;

final class LombokEqualsAndHashcodeModCommandAction extends BaseLombokModCommandAction {
  LombokEqualsAndHashcodeModCommandAction(@NotNull PsiClass element) {
    super(element);
  }

  @Override
  public @NotNull String getFamilyName() {
    return LombokBundle.message("action.defaultLombokEqualsAndHashcode.text");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiClass element, @NotNull ModPsiUpdater updater) {
    processClass(element);
  }

  static void processClass(@NotNull PsiClass psiClass) {
    final PsiMethod equalsMethod = findPublicNonStaticMethod(psiClass, "equals", PsiTypes.booleanType(),
                                                             PsiType.getJavaLangObject(psiClass.getManager(), psiClass.getResolveScope()));
    if (null != equalsMethod) {
      equalsMethod.delete();
    }

    final PsiMethod hashCodeMethod = findPublicNonStaticMethod(psiClass, "hashCode", PsiTypes.intType());
    if (null != hashCodeMethod) {
      hashCodeMethod.delete();
    }

    addAnnotation(psiClass, LombokClassNames.EQUALS_AND_HASHCODE);
  }
}
