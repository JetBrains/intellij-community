// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.fileTemplate;

import com.intellij.ide.fileTemplates.DefaultCreateFromTemplateHandler;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.uiDesigner.GuiFormFileType;
import org.jetbrains.annotations.NotNull;


public class CreateFormFromTemplateHandler extends DefaultCreateFromTemplateHandler {
  @Override
  public boolean handlesTemplate(final @NotNull FileTemplate template) {
    FileType fileType = FileTypeManagerEx.getInstanceEx().getFileTypeByExtension(template.getExtension());
    return fileType.equals(GuiFormFileType.INSTANCE);
  }

  @Override
  public boolean canCreate(final PsiDirectory @NotNull [] dirs) {
    for (PsiDirectory dir : dirs) {
      if (JavaDirectoryService.getInstance().getPackage(dir) != null) return true;
    }
    return false;
  }
}
