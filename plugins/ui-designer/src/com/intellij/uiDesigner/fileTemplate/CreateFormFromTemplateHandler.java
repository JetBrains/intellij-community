/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.fileTemplate;

import com.intellij.ide.fileTemplates.DefaultCreateFromTemplateHandler;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;

/**
 * @author yole
 */
public class CreateFormFromTemplateHandler extends DefaultCreateFromTemplateHandler {
  public boolean handlesTemplate(final FileTemplate template) {
    FileType fileType = FileTypeManagerEx.getInstanceEx().getFileTypeByExtension(template.getExtension());
    return fileType.equals(StdFileTypes.GUI_DESIGNER_FORM);
  }

  public boolean canCreate(final PsiDirectory[] dirs) {
    for (PsiDirectory dir : dirs) {
      if (JavaDirectoryService.getInstance().getPackage(dir) != null) return true;
    }
    return false;
  }
}
