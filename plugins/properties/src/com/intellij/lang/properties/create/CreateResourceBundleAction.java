/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.properties.create;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class CreateResourceBundleAction extends CreateElementActionBase {

  protected CreateResourceBundleAction() {
    super(PropertiesBundle.messagePointer("create.resource.bundle.dialog.action.title"), Presentation.NULL_STRING, AllIcons.FileTypes.Properties);
  }

  @Override
  protected PsiElement @NotNull [] invokeDialog(@NotNull Project project, @NotNull PsiDirectory directory) {
    final CreateResourceBundleDialogComponent.Dialog dialog = new CreateResourceBundleDialogComponent.Dialog(project, directory, null);
    if (dialog.showAndGet()) {
      return dialog.getCreatedFiles();
    }
    else {
      return PsiElement.EMPTY_ARRAY;
    }
  }

  @Override
  protected PsiElement @NotNull [] create(@NotNull String newName, @NotNull PsiDirectory directory) throws Exception {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  protected String getErrorTitle() {
    return PropertiesBundle.message("create.resource.bundle.dialog.error");
  }

  @Override
  protected @NotNull String getActionName(@NotNull PsiDirectory directory, @NotNull String newName) {
    return PropertiesBundle.message("create.resource.bundle.dialog.action.name", newName);
  }
}
