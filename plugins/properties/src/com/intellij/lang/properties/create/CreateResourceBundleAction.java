/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties.create;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class CreateResourceBundleAction extends CreateElementActionBase {

  protected CreateResourceBundleAction() {
    super(PropertiesBundle.message("create.resource.bundle.dialog.action.title"), null, AllIcons.FileTypes.Properties);
  }

  @NotNull
  @Override
  protected PsiElement[] invokeDialog(Project project, PsiDirectory directory) {
    final CreateResourceBundleDialogComponent.Dialog dialog = new CreateResourceBundleDialogComponent.Dialog(project, directory, null);
    if (dialog.showAndGet()) {
      return dialog.getCreatedFiles();
    } else {
      return PsiElement.EMPTY_ARRAY;
    }
  }

  @NotNull
  @Override
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  protected String getErrorTitle() {
    return PropertiesBundle.message("create.resource.bundle.dialog.error");
  }

  @Override
  protected String getCommandName() {
    return PropertiesBundle.message("create.resource.bundle.dialog.command");
  }

  @Override
  protected String getActionName(PsiDirectory directory, String newName) {
    return PropertiesBundle.message("create.resource.bundle.dialog.action.name", newName);
  }
}
