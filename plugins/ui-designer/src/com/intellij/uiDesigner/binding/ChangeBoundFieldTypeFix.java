// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.binding;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public class ChangeBoundFieldTypeFix extends PsiUpdateModCommandAction<PsiField> {
  private final PsiType myTypeToSet;

  public ChangeBoundFieldTypeFix(PsiField field, PsiType typeToSet) {
    super(field);
    myTypeToSet = typeToSet;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("uidesigner.change.bound.field.type");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiField field, @NotNull ModPsiUpdater updater) {
    PsiTypeElement element = field.getTypeElement();
    if (element == null) return;
    element.replace(JavaPsiFacade.getInstance(context.project()).getElementFactory().createTypeElement(myTypeToSet));
  }
}
