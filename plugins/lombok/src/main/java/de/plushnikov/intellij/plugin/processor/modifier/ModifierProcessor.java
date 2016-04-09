package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexej Kubarev
 */
public interface ModifierProcessor {

  boolean isSupported(@NotNull PsiModifierList modifierList, @NotNull String name);
  Boolean hasModifierProperty(@NotNull PsiModifierList modifierList, @NotNull String name);
}
