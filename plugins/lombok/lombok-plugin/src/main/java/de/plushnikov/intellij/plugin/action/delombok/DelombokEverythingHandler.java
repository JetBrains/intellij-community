package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class DelombokEverythingHandler implements CodeInsightActionHandler {

  private final DelombokGetterHandler delombokGetterHandler;
  private final DelombokSetterHandler delombokSetterHandler;

  public DelombokEverythingHandler() {
    delombokGetterHandler = new DelombokGetterHandler();
    delombokSetterHandler = new DelombokSetterHandler();
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiClass psiClass = OverrideImplementUtil.getContextClass(project, editor, file, false);
    if (null != psiClass) {
      processClass(project, psiClass);

      UndoUtil.markPsiFileForUndo(file);
    }
  }

  private void processClass(Project project, PsiClass psiClass) {
    delombokGetterHandler.processClass(project, psiClass);
    delombokSetterHandler.processClass(project, psiClass);

  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

}
