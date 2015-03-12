package com.jetbrains.python.refactoring.convert;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
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

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyConvertPackageToModuleAction extends BaseRefactoringAction {
  private static final Logger LOG = Logger.getInstance(PyConvertPackageToModuleAction.class);
  private static final String ID = "py.refactoring.convert.package.to.module";

  @Override
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  protected boolean isEnabledOnElements(@NotNull PsiElement[] elements) {
    if (elements.length == 1) {
      final PsiDirectory pyPackage = getPackageDir(elements[0]);
      return pyPackage != null && !isSpecialDirectory(pyPackage);

    }
    return false;
  }

  @Nullable
  private static PsiDirectory getPackageDir(@NotNull PsiElement elem) {
    if (elem instanceof PsiDirectory && PyUtil.isPackage(((PsiDirectory)elem), null)) {
      return (PsiDirectory)elem;
    }
    else if (elem instanceof PsiFile && PyUtil.isPackage(((PsiFile)elem))) {
      return ((PsiFile)elem).getParent();
    }
    return null;
  }

  private static boolean isSpecialDirectory(@NotNull PsiDirectory element) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(element);
    return module == null || (PyUtil.getSourceRoots(module).contains(element.getVirtualFile()));
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
        final PsiDirectory pyPackage = getPackageDir(file);
        if (pyPackage != null) {
          createModuleFromPackage(pyPackage);
        }
      }

      @Override
      public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
        if (elements.length == 1) {
          final PsiDirectory pyPackage = getPackageDir(elements[0]);
          if (pyPackage != null) {
            createModuleFromPackage(pyPackage);
          }
        }
      }
    };
  }

  @VisibleForTesting
  public void createModuleFromPackage(@NotNull final PsiDirectory pyPackage) {
    if (pyPackage.getParent() == null) {
      return;
    }

    final String packageName = pyPackage.getName();
    if (!isEmptyPackage(pyPackage)) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"),
                                             PyBundle.message("refactoring.convert.package.to.module.error.not.empty.package", packageName),
                                             ID, pyPackage.getProject());
      return;
    }
    final VirtualFile parentDirVFile = pyPackage.getParent().getVirtualFile();
    final String moduleName = packageName + PyNames.DOT_PY;
    final VirtualFile existing = parentDirVFile.findChild(moduleName);
    if (existing != null) {
      final String message;
      if (existing.isDirectory()) {
        message = PyBundle.message("refactoring.convert.module.to.package.error.directory.exists", moduleName);
      }
      else {
        message = PyBundle.message("refactoring.convert.module.to.package.error.file.exists", moduleName);
      }
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), message, ID, pyPackage.getProject());
      return;
    }
    final PsiFile initPy = pyPackage.findFile(PyNames.INIT_DOT_PY);
    WriteCommandAction.runWriteCommandAction(pyPackage.getProject(), new Runnable() {
      public void run() {
        try {
          if (initPy != null) {
            final VirtualFile initPyVFile = initPy.getVirtualFile();
            initPyVFile.rename(PyConvertPackageToModuleAction.this, moduleName);
            initPyVFile.move(PyConvertPackageToModuleAction.this, parentDirVFile);
          }
          else {
            PyUtil.getOrCreateFile(parentDirVFile.getPath() + "/" + moduleName, pyPackage.getProject());
          }
          pyPackage.getVirtualFile().delete(PyConvertPackageToModuleAction.this);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    });
  }

  private static boolean isEmptyPackage(@NotNull PsiDirectory pyPackage) {
    final PsiElement[] children = pyPackage.getChildren();
    if (children.length == 1) {
      final PyFile onlyFile = as(children[0], PyFile.class);
      return onlyFile != null && onlyFile.getName().equals(PyNames.INIT_DOT_PY);
    }
    return children.length == 0;
  }
}
