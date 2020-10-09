package de.plushnikov.intellij.plugin.extension;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * "Rename"-Handler Vetoer to disable renaming not supported lombok generated  methods
 */
public class LombokElementRenameVetoHandler implements RenameHandler {
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    final PsiElement element = PsiElementRenameHandler.getElement(dataContext);
    return element instanceof LombokLightClassBuilder ||
      ((element instanceof LombokLightMethodBuilder || element instanceof LombokLightFieldBuilder)
        && element.getNavigationElement() instanceof PsiAnnotation);
  }

  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, @Nullable DataContext dataContext) {
    invokeInner(project, editor);
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, @Nullable DataContext dataContext) {
    Editor editor = dataContext == null ? null : PlatformDataKeys.EDITOR.getData(dataContext);
    invokeInner(project, editor);
  }

  private void invokeInner(Project project, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor,
      RefactoringBundle.getCannotRefactorMessage("This element cannot be renamed."),
      RefactoringBundle.message("rename.title"), null);
  }
}
