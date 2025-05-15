package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import org.jetbrains.annotations.NotNull;

public abstract class BaseLombokAction extends BaseGenerateAction {

  protected BaseLombokAction(CodeInsightActionHandler handler) {
    super(handler);
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return psiFile.isWritable() && super.isValidForFile(project, editor, psiFile) &&
           LombokLibraryUtil.hasLombokLibrary(project);
  }
}
