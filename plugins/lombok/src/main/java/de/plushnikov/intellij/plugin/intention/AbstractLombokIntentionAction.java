package de.plushnikov.intellij.plugin.intention;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import de.plushnikov.intellij.plugin.activity.LombokProjectValidatorActivity;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractLombokIntentionAction extends PsiElementBaseIntentionAction {

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return LombokProjectValidatorActivity.hasLombokLibrary(project);
  }
}
