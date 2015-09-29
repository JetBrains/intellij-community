package com.jetbrains.python.refactoring.convertModulePackage;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.refactoring.PyBaseRefactoringAction;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public abstract class PyBaseConvertModulePackageAction extends PyBaseRefactoringAction {
  @Override
  protected final boolean isAvailableInEditorOnly() {
    return false;
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
