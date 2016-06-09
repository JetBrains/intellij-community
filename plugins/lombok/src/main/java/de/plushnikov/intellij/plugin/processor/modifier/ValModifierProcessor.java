package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import de.plushnikov.intellij.plugin.processor.ValProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexej Kubarev
 */
public class ValModifierProcessor implements ModifierProcessor {

  @Override
  public boolean isSupported(@NotNull PsiModifierList modifierList, @NotNull String name) {

    // Val only enforces "final"
    if (!PsiModifier.FINAL.equals(name)) {
      return false;
    }

    final PsiElement parent = modifierList.getParent();

    return (parent instanceof PsiLocalVariable && ValProcessor.isVal((PsiLocalVariable) parent));
  }

  @Override
  public Boolean hasModifierProperty(@NotNull PsiModifierList modifierList, @NotNull String name) {
    return Boolean.TRUE;
  }
}
