package de.plushnikov.intellij.plugin.intention;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
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
    final Module module = ModuleUtilCore.findModuleForFile(element.getContainingFile());
    if (!LombokLibraryUtil.hasLombokClasses(module)) return null;
    return super.getPresentation(context, element);
  }
}
