package com.intellij.ide.projectView.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;

/**
 * @author cdr
 */
public class DirectoryUrl extends AbstractUrl {
  private static final String ELEMENT_TYPE = "directory";

  public DirectoryUrl(String url, String moduleName) {
    super(url, moduleName,ELEMENT_TYPE);
  }

  public Object[] createPath(Project project) {
    final Module module = moduleName != null ? ModuleManager.getInstance(project).findModuleByName(moduleName) : null;
    if (module == null) return null;
    final VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    VirtualFile file = virtualFileManager.findFileByUrl(url);
    if (file == null) return null;
    final PsiDirectory directory = PsiManager.getInstance(project).findDirectory(file);
    if (directory == null) return null;
    return new Object[]{directory};
  }

  protected AbstractUrl createUrl(String moduleName, String url) {
      return new DirectoryUrl(url, moduleName);
  }

  public AbstractUrl createUrlByElement(Object element) {
    if (element instanceof PsiDirectory) {
      Project project = ((PsiDirectory)element).getProject();
      final VirtualFile virtualFile = ((PsiDirectory)element).getVirtualFile();
      if (virtualFile == null) return null;
      final Module module = ModuleUtil.getModuleForFile(project, virtualFile);
      if (module == null) return null;
      return new DirectoryUrl(virtualFile.getUrl(), module.getName());
    }
    return null;
  }
}
