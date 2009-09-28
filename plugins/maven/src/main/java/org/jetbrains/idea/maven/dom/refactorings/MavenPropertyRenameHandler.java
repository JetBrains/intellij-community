package org.jetbrains.idea.maven.dom.refactorings;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.references.MavenTargetUtil;

public class MavenPropertyRenameHandler extends PsiElementRenameHandler {
  @Override
  public boolean isAvailableOnDataContext(DataContext context) {
    return findTarget(context) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    invoke(project, PsiElement.EMPTY_ARRAY, dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    PsiElement element = elements.length == 1 ? elements[0] : null;
    if (element == null) element = findTarget(dataContext);

    RenameDialog dialog = new RenameDialog(project, element, null, PlatformDataKeys.EDITOR.getData(dataContext));
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      String name = DEFAULT_NAME.getData(dataContext);
      dialog.performRename(name);
    }
    else {
      dialog.show();
    }
  }

  private PsiElement findTarget(DataContext context) {
    PsiElement target = MavenTargetUtil.getRefactorTarget(PlatformDataKeys.EDITOR.getData(context),
                                                          LangDataKeys.PSI_FILE.getData(context));
    return isVetoed(target) ? null : target;
  }
}
