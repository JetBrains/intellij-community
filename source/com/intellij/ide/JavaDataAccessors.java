package com.intellij.ide;

import com.intellij.psi.*;
import com.intellij.openapi.actionSystem.DataContext;

public class JavaDataAccessors {
  public static final DataAccessor<PsiPackage> FILE_PACKAGE = new DataAccessor<PsiPackage>() {
    public PsiPackage getImpl(DataContext dataContext) throws NoDataException {
      PsiFile psiFile = DataAccessors.PSI_FILE.getNotNull(dataContext);
      PsiDirectory containingDirectory = psiFile.getContainingDirectory();
      if (containingDirectory == null || !containingDirectory.isValid()) return null;
      return JavaDirectoryService.getInstance().getPackage(containingDirectory);
    }
  };
  public static final DataAccessor<PsiJavaFile> PSI_JAVA_FILE = DataAccessor.SubClassDataAccessor.create(DataAccessors.PSI_FILE, PsiJavaFile.class);

  private JavaDataAccessors() {
  }
}
