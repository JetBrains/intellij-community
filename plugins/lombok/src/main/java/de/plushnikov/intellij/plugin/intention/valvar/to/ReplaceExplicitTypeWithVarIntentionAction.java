package de.plushnikov.intellij.plugin.intention.valvar.to;

import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.PsiModifier.FINAL;

public final class ReplaceExplicitTypeWithVarIntentionAction extends AbstractReplaceExplicitTypeWithVariableIntentionAction {

  public ReplaceExplicitTypeWithVarIntentionAction() {
    super(LombokClassNames.VAR);
  }

  @Override
  protected boolean isAvailableOnDeclarationCustom(@NotNull PsiDeclarationStatement declarationStatement, @NotNull PsiLocalVariable localVariable) {
    return isNotFinal(localVariable);
  }

  @Override
  protected void executeAfterReplacing(PsiVariable psiVariable) {
  }

  @Override
  public boolean isAvailableOnVariable(@NotNull PsiVariable psiVariable) {
    if (!(psiVariable instanceof PsiParameter psiParameter)) {
      return false;
    }
    PsiElement declarationScope = psiParameter.getDeclarationScope();
    if (!(declarationScope instanceof PsiForStatement) && !(declarationScope instanceof PsiForeachStatement)) {
      return false;
    }
    PsiTypeElement typeElement = psiParameter.getTypeElement();
    return typeElement != null && !typeElement.isInferredType() && isNotFinal(psiParameter);
  }

  private static boolean isNotFinal(@NotNull PsiVariable variable) {
    PsiModifierList modifierList = variable.getModifierList();
    return modifierList == null || !modifierList.hasExplicitModifier(FINAL);
  }
}
