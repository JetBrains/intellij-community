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
