package com.intellij.psi.impl.file;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.PsiManagerImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PsiJavaDirectoryFactory extends PsiDirectoryFactoryImpl {
  public PsiJavaDirectoryFactory(final PsiManagerImpl manager) {
    super(manager);
  }

  public PsiDirectory createDirectory(final VirtualFile file) {
    return new PsiJavaDirectoryImpl(getManager(), file);
  }

  @NotNull
  public String getQualifiedName(@NotNull final PsiDirectory directory) {
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (aPackage != null) {
      return aPackage.getQualifiedName();
    }
    else {
      return directory.getVirtualFile().getPresentableUrl();
    }
  }

  public String getComment(@NotNull final PsiDirectory directory, final boolean forceLocation) {
    if (!forceLocation) {
      final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(getManager().getProject());
      final String informationString = coverageDataManager.getDirCoverageInformationString(directory);
      if (informationString != null) return informationString;
    }
    return super.getComment(directory, forceLocation);
  }
}
