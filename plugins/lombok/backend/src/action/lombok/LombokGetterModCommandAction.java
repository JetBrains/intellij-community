// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lombok.backend.action.lombok;

import com.intellij.lombok.shared.LombokBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PropertyUtilBase;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static de.plushnikov.intellij.plugin.util.LombokAccessorMethodUtil.findFieldIfMethodIsSimpleGetter;

final class LombokGetterModCommandAction extends BaseLombokModCommandAction {
  LombokGetterModCommandAction(@NotNull PsiClass element) {
    super(element);
  }

  @Override
  public @NotNull String getFamilyName() {
    return LombokBundle.message("action.defaultLombokGetter.text");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiClass element, @NotNull ModPsiUpdater updater) {
    processClass(element);
  }

  static void processClass(@NotNull PsiClass psiClass) {
    final Map<PsiField, PsiMethod> fieldMethodMap = new HashMap<>();
    for (PsiField psiField : psiClass.getFields()) {
      PsiMethod propertyGetter =
        PropertyUtilBase.findPropertyGetter(psiClass, psiField.getName(), psiField.hasModifierProperty(PsiModifier.STATIC), false);

      if (null != propertyGetter) {
        final PsiField getterField = findFieldIfMethodIsSimpleGetter(propertyGetter);
        if (psiField.equals(getterField)) {
          fieldMethodMap.put(psiField, propertyGetter);
        }
      }
    }

    processIntern(fieldMethodMap, psiClass, LombokClassNames.GETTER);
  }
}
