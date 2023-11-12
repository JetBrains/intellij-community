package de.plushnikov.intellij.plugin.intention;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ReplaceSynchronizedWithLombokAction extends AbstractLombokIntentionAction {
  @Override
  public @NotNull String getFamilyName() {
    return LombokBundle.message("replace.synchronized.lombok.intention");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    final Presentation presentation = super.getPresentation(context, element);
    if (presentation == null) return null;

    final PsiModifierList psiModifierList = getElementToReplace(element);
    if (null == psiModifierList) return null;

    return presentation;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiModifierList psiModifierList = getElementToReplace(element);
    if (null != psiModifierList) {
      psiModifierList.setModifierProperty(PsiModifier.SYNCHRONIZED, false);

      final PsiAnnotation addedAnnotation = psiModifierList.addAnnotation(LombokClassNames.SYNCHRONIZED);
      JavaCodeStyleManager.getInstance(context.project()).shortenClassReferences(Objects.requireNonNull(addedAnnotation));
    }
  }

  private static @Nullable PsiModifierList getElementToReplace(@NotNull PsiElement element) {
    final PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (parent instanceof PsiMethod psiMethod && !psiMethod.isConstructor()) {
      final PsiModifierList psiModifierList = psiMethod.getModifierList();
      if (psiModifierList.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
        return psiModifierList;
      }
    }
    return null;
  }
}
