package com.intellij.psi.impl.file;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.PsiManagerImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PsiJavaDirectoryFactory extends PsiDirectoryFactory {
  private final PsiManagerImpl myManager;

  public PsiJavaDirectoryFactory(final PsiManagerImpl manager) {

    myManager = manager;
  }

  public PsiDirectory createDirectory(final VirtualFile file) {
    return new PsiJavaDirectoryImpl(myManager, file);
  }

  @NotNull
  public String getQualifiedName(@NotNull final PsiDirectory directory, final boolean presentable) {
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (aPackage != null) {
      final String qualifiedName = aPackage.getQualifiedName();
      if (qualifiedName.length() > 0) return qualifiedName;
      if (presentable) {
        return PsiBundle.message("default.package.presentation") + " (" + directory.getVirtualFile().getPresentableUrl() + ")";
      }
      return "";
    }
    return presentable ? directory.getVirtualFile().getPresentableUrl() : "";
  }
}
