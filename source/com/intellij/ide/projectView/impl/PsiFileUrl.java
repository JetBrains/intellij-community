package com.intellij.ide.projectView.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NonNls;

public class PsiFileUrl extends AbstractUrl {

  @NonNls private static final String ELEMENT_TYPE = "psiFile";

  public PsiFileUrl(String url, String moduleName) {
    super(url, moduleName, ELEMENT_TYPE);
  }

  public Object[] createPath(Project project) {
    final VirtualFile fileByUrl = VirtualFileManager.getInstance().findFileByUrl(url);
    if (fileByUrl == null || !fileByUrl.isValid()){
      return null;
    }
    return new Object[]{PsiManager.getInstance(project).findFile(fileByUrl)};
  }

  protected AbstractUrl createUrl(String moduleName, String url) {
      return new PsiFileUrl(url, moduleName);
  }

  public AbstractUrl createUrlByElement(Object element) {
    if (element instanceof PsiFile) {
      VirtualFile file = ((PsiFile)element).getVirtualFile();
      if (file != null){
        return new PsiFileUrl(file.getUrl(), null);
      }
    }
    return null;
  }
}
