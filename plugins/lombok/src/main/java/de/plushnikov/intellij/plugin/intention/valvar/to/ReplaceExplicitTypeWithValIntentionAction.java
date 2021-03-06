package de.plushnikov.intellij.plugin.intention.valvar.to;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.PsiModifier.FINAL;

public class ReplaceExplicitTypeWithValIntentionAction extends AbstractReplaceExplicitTypeWithVariableIntentionAction {

  public ReplaceExplicitTypeWithValIntentionAction() {
    super(LombokClassNames.VAL);
  }

  @Override
  protected boolean isAvailableOnDeclarationCustom(@NotNull PsiDeclarationStatement declarationStatement, @NotNull PsiLocalVariable localVariable) {
    return !(declarationStatement.getParent() instanceof PsiForStatement);
  }

  @Override
  protected void executeAfterReplacing(PsiVariable psiVariable) {
    PsiModifierList modifierList = psiVariable.getModifierList();
    if (modifierList != null) {
      modifierList.setModifierProperty(FINAL, false);
    }
  }

  @Override
  public boolean isAvailableOnVariable(PsiVariable psiVariable) {
    if (!(psiVariable instanceof PsiParameter)) {
      return false;
    }
    PsiParameter parameter = (PsiParameter) psiVariable;
    if (!(parameter.getDeclarationScope() instanceof PsiForeachStatement)) {
      return false;
    }
    PsiTypeElement typeElement = parameter.getTypeElement();
    return typeElement == null || !typeElement.isInferredType();
  }
}
