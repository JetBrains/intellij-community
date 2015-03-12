package com.jetbrains.python.refactoring.convert;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public abstract class PyBaseConvertRefactoringAction extends BaseRefactoringAction {
  @Override
  protected final boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element,
                                                        @NotNull Editor editor,
                                                        @NotNull PsiFile file,
                                                        @NotNull DataContext context) {
    return false;
  }

  @Override
  protected final boolean isAvailableForLanguage(Language language) {
    return language.isKindOf(PythonLanguage.getInstance());
  }

  @Override
  protected boolean isAvailableForFile(PsiFile file) {
    return isAvailableForLanguage(file.getLanguage());
  }

  /**
   * Show standard error dialog containing message about unexpected presense of given file or directory.
   *
   * @param file    file or directory to warn about
   * @param id      ID of refactoring as {@link CommonRefactoringUtil#showErrorMessage} requires
   * @param project active project
   */
  protected final void showFileExistsErrorMessage(@NotNull VirtualFile file, @NotNull String id, @NotNull Project project) {
    final String message;
    if (file.isDirectory()) {
      message = PyBundle.message("refactoring.error.directory.exists", file.getName());
    }
    else {
      message = PyBundle.message("refactoring.error.file.exists", file.getName());
    }
    CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), message, id, project);
  }
}
