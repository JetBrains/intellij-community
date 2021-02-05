package de.plushnikov.intellij.plugin.intention.valvar;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.intention.AbstractLombokIntentionAction;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractValVarIntentionAction extends AbstractLombokIntentionAction implements LowPriorityAction {

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!super.isAvailable(project, editor, element)) {
      return false;
    }
    if (element instanceof PsiCompiledElement || !canModify(element) || !element.getLanguage().is(JavaLanguage.INSTANCE)) {
      return false;
    }

    setText(getFamilyName());

    PsiParameter parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class, false, PsiClass.class, PsiCodeBlock.class);
    if (parameter != null) {
      return isAvailableOnVariable(parameter);
    }
    PsiDeclarationStatement context = PsiTreeUtil.getParentOfType(element, PsiDeclarationStatement.class, false, PsiClass.class, PsiCodeBlock.class);
    return context != null && isAvailableOnDeclarationStatement(context);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
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
