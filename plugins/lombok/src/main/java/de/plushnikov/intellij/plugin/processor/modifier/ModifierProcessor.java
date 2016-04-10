package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

/**
 * To support augmentation of {@link PsiModifierList} properties, processors should implement this interface.
 *
 * @author Alexej Kubarev
 * @see com.intellij.psi.augment.PsiAugmentProvider#hasModifierProperty(PsiModifierList, String)
 */
public interface ModifierProcessor {

  /**
   * Validates if this {@link ModifierProcessor} implementation supports provided property on a {@link PsiModifierList}.
   * This method <strong>should not</strong> do heavy computations and defer them to {@link #hasModifierProperty(PsiModifierList, String)} instead.
   *
   * @param modifierList List the property is queried on
   * @param name Name of the property
   * @return true if supported and therefore may be passed to {@link #hasModifierProperty(PsiModifierList, String)}, false otherwise
   */
  boolean isSupported(@NotNull PsiModifierList modifierList, @NotNull String name);

  /**
   * Compute correct response for {@link com.intellij.psi.augment.PsiAugmentProvider#hasModifierProperty(PsiModifierList, String)}.
   * Must respond with {@literal null} if property existence cannot be identified.
   * @param modifierList List the property is queried on
   * @param name Name of the property
   * @return Boolean value if property existence could be identified, {@literal null} otherwise.
   */
  Boolean hasModifierProperty(@NotNull PsiModifierList modifierList, @NotNull String name);
}
