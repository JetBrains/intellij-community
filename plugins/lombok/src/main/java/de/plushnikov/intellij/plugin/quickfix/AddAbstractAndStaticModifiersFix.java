package de.plushnikov.intellij.plugin.quickfix;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import org.jetbrains.annotations.NotNull;

public class AddAbstractAndStaticModifiersFix extends ModCommandQuickFix {
  private final String myName;
  private final SmartPsiElementPointer<PsiElement> myStartElement;

  public AddAbstractAndStaticModifiersFix(@NotNull PsiClass psiClass) {
    myName = psiClass.getName();
    myStartElement =
      SmartPointerManager.getInstance(psiClass.getProject()).createSmartPsiElementPointer(psiClass, psiClass.getContainingFile());
  }

  @Override
  public final @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement psiElement = myStartElement.getElement();
    if (null != psiElement) {
      return ModCommand.psiUpdate(psiElement, (e, updater) -> applyFix(e, updater));
    }
    else {
      return ModCommand.nop();
    }
  }

  private static void applyFix(@NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiModifierListOwner modifierListOwner = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class, false);
    if (modifierListOwner == null) {
      return;
    }
    final PsiModifierList modifiers = modifierListOwner.getModifierList();
    if (modifiers != null) {
      modifiers.setModifierProperty(PsiModifier.ABSTRACT, true);
      modifiers.setModifierProperty(PsiModifier.STATIC, true);
    }
  }

  @Override
  @NotNull
  public String getName() {
    return LombokBundle.message("make.abstract.and.static.modifier.quickfix", myName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return LombokBundle.message("make.abstract.and.static.modifier.quickfix.family.name");
  }
}