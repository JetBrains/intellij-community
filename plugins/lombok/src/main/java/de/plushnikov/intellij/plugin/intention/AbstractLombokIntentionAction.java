package de.plushnikov.intellij.plugin.intention;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiElement;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractLombokIntentionAction extends PsiUpdateModCommandAction<PsiElement> {

  public AbstractLombokIntentionAction() {
    super(PsiElement.class);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (!LombokLibraryUtil.hasLombokLibrary(context.project())) return null;
    return super.getPresentation(context, element);
  }
}
