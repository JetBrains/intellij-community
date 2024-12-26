package de.plushnikov.intellij.plugin.quickfix;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import de.plushnikov.intellij.plugin.LombokBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddAbstractAndStaticModifiersFix extends PsiUpdateModCommandAction<PsiClass> {
  private final String myName;

  public AddAbstractAndStaticModifiersFix(@NotNull PsiClass psiClass) {
    super(psiClass);
    myName = psiClass.getName();
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiClass psiClass, @NotNull ModPsiUpdater updater) {
    final PsiModifierList modifiers = psiClass.getModifierList();
    if (modifiers != null) {
      modifiers.setModifierProperty(PsiModifier.ABSTRACT, true);
      modifiers.setModifierProperty(PsiModifier.STATIC, true);
    }
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass element) {
    return Presentation.of(LombokBundle.message("make.abstract.and.static.modifier.quickfix", myName));
  }

  @Override
  public @NotNull String getFamilyName() {
    return LombokBundle.message("make.abstract.and.static.modifier.quickfix.family.name");
  }
}