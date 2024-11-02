package de.plushnikov.intellij.plugin.intention.valvar;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.intention.AbstractLombokIntentionAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractValVarIntentionAction extends AbstractLombokIntentionAction {

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (super.getPresentation(context, element) == null) {
      return null;
    }
    if (element instanceof PsiCompiledElement || !BaseIntentionAction.canModify(element) || !element.getLanguage().is(JavaLanguage.INSTANCE)) {
      return null;
    }

    PsiParameter parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class, false, PsiClass.class, PsiCodeBlock.class);
    boolean available;
    if (parameter != null) {
      available = isAvailableOnVariable(parameter);
    } else {
      PsiDeclarationStatement decl =
        PsiTreeUtil.getParentOfType(element, PsiDeclarationStatement.class, false, PsiClass.class, PsiCodeBlock.class);
      available = decl != null && isAvailableOnDeclarationStatement(decl);
    }
    if (!available) return null;
    return Presentation.of(getFamilyName()).withPriority(PriorityAction.Priority.LOW);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiDeclarationStatement declarationStatement = PsiTreeUtil.getParentOfType(element, PsiDeclarationStatement.class);

    if (declarationStatement != null) {
      invokeOnDeclarationStatement(declarationStatement);
      return;
    }

    final PsiParameter parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class);
    if (parameter != null) {
      invokeOnVariable(parameter);
    }
  }

  public abstract boolean isAvailableOnVariable(PsiVariable psiVariable);

  public abstract boolean isAvailableOnDeclarationStatement(PsiDeclarationStatement psiDeclarationStatement);

  public abstract void invokeOnVariable(PsiVariable psiVariable);

  public abstract void invokeOnDeclarationStatement(PsiDeclarationStatement psiDeclarationStatement);
}
