package de.plushnikov.intellij.plugin.intention.valvar.to;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.PsiModifier.FINAL;

public class ReplaceExplicitTypeWithVarIntentionAction extends AbstractReplaceExplicitTypeWithVariableIntentionAction {

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
    if (!(psiVariable instanceof PsiParameter)) {
      return false;
    }
    PsiParameter psiParameter = (PsiParameter) psiVariable;
    PsiElement declarationScope = psiParameter.getDeclarationScope();
    if (!(declarationScope instanceof PsiForStatement) && !(declarationScope instanceof PsiForeachStatement)) {
      return false;
    }
    PsiTypeElement typeElement = psiParameter.getTypeElement();
    return typeElement != null && !typeElement.isInferredType() && isNotFinal(psiParameter);
  }

  private boolean isNotFinal(@NotNull PsiVariable variable) {
    PsiModifierList modifierList = variable.getModifierList();
    return modifierList == null || !modifierList.hasExplicitModifier(FINAL);
  }
}
