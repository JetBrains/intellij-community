package de.plushnikov.intellij.plugin.intention.valvar.to;

import com.intellij.psi.PsiDeclarationStatement;
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

public final class ReplaceExplicitTypeWithValIntentionAction extends AbstractReplaceExplicitTypeWithVariableIntentionAction {

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
    if (!(psiVariable instanceof PsiParameter parameter)) {
      return false;
    }
    if (!(parameter.getDeclarationScope() instanceof PsiForeachStatement)) {
      return false;
    }
    PsiTypeElement typeElement = parameter.getTypeElement();
    return typeElement == null || !typeElement.isInferredType();
  }
}
