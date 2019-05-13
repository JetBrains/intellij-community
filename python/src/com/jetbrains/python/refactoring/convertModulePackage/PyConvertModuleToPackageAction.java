package com.jetbrains.python.refactoring.convertModulePackage;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Mikhail Golubev
 */
public class PyConvertModuleToPackageAction extends PyBaseConvertModulePackageAction {
  public static final String ID = "py.refactoring.convert.module.to.package";
  private static final Logger LOG = Logger.getInstance(PyConvertModuleToPackageAction.class);

  @Override
  protected boolean isEnabledOnElementsOutsideEditor(@NotNull PsiElement[] elements) {
    if (elements.length == 1) {
      return elements[0] instanceof PyFile && !PyUtil.isPackage((PyFile)elements[0]);
    }
    return false;
  }

  @Nullable
  @Override
  protected RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new RefactoringActionHandler() {
      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        createPackageFromModule((PyFile)file);
      }

      @Override
      public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
        createPackageFromModule(((PyFile)elements[0]));
      }
    };
  }

  @VisibleForTesting
  public void createPackageFromModule(@NotNull final PyFile file) {
    final VirtualFile vFile = file.getVirtualFile();
    final VirtualFile parentDir = vFile.getParent();
    final String newPackageName = vFile.getNameWithoutExtension();
    final VirtualFile existing = parentDir.findChild(newPackageName);
    if (existing != null) {
      showFileExistsErrorMessage(existing, ID, file.getProject());
      return;
    }
    WriteCommandAction.runWriteCommandAction(file.getProject(), () -> {
      try {
        final VirtualFile packageDir = parentDir.createChildDirectory(this, newPackageName);
        vFile.move(this, packageDir);
        vFile.rename(this, PyNames.INIT_DOT_PY);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    });
  }
}
