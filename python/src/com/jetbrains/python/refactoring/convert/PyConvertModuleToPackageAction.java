package com.jetbrains.python.refactoring.convert;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Mikhail Golubev
 */
public class PyConvertModuleToPackageAction extends BaseRefactoringAction {
  public static final String ID = "py.refactoring.convert.module.to.package";
  private static final Logger LOG = Logger.getInstance(PyConvertModuleToPackageAction.class);

  @Override
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  protected boolean isEnabledOnElements(@NotNull PsiElement[] elements) {
    if (elements.length == 1) {
      return elements[0] instanceof PyFile && !PyUtil.isPackage((PyFile)elements[0]);
    }
    return false;
  }

  @Override
  protected boolean isAvailableForLanguage(Language language) {
    return language.isKindOf(PythonLanguage.getInstance());
  }

  @Override
  protected boolean isAvailableForFile(PsiFile file) {
    return isAvailableForLanguage(file.getLanguage());
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
      final String message;
      if (existing.isDirectory()) {
        message = PyBundle.message("refactoring.convert.module.to.package.error.directory.exists", newPackageName);
      }
      else {
        message = PyBundle.message("refactoring.convert.module.to.package.error.file.exists", newPackageName);
      }
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), message, ID, file.getProject());
    }
    WriteCommandAction.runWriteCommandAction(file.getProject(), new Runnable() {
      public void run() {
        try {
          final VirtualFile packageDir = parentDir.createChildDirectory(PyConvertModuleToPackageAction.this, newPackageName);
          vFile.move(PyConvertModuleToPackageAction.this, packageDir);
          vFile.rename(PyConvertModuleToPackageAction.this, PyNames.INIT_DOT_PY);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    });
  }
}
