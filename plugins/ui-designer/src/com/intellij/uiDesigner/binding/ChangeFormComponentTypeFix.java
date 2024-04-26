// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.binding;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public class ChangeFormComponentTypeFix implements ModCommandAction {
  private final PsiPlainTextFile myFormFile;
  private final String myFieldName;
  private final String myComponentTypeToSet;

  public ChangeFormComponentTypeFix(PsiPlainTextFile formFile, String fieldName, PsiType componentTypeToSet) {
    myFormFile = formFile;
    myFieldName = fieldName;
    if (componentTypeToSet instanceof PsiClassType) {
      PsiClass psiClass = ((PsiClassType)componentTypeToSet).resolve();
      if (psiClass != null) {
        myComponentTypeToSet = ClassUtil.getJVMClassName(psiClass);
      }
      else {
        myComponentTypeToSet = ((PsiClassType) componentTypeToSet).rawType().getCanonicalText();
      }
    }
    else {
      myComponentTypeToSet = componentTypeToSet.getCanonicalText();
    }
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("uidesigner.change.gui.component.type");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    return Presentation.of(getFamilyName());
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    return FormReferenceProvider.setGUIComponentType(myFormFile, myFieldName, myComponentTypeToSet);
  }
}
