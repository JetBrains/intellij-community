package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * To support augmentation of {@link PsiModifierList} properties, processors should implement this interface.
 *
 * @author Alexej Kubarev
 * @see com.intellij.psi.augment.PsiAugmentProvider#transformModifiers(PsiModifierList, Set<String>)
 */
public interface ModifierProcessor {

  /**
   * Validates if this {@link ModifierProcessor} implementation supports provided property on a {@link PsiModifierList}.
   * This method <strong>should not</strong> do heavy computations and defer them to {@link #transformModifiers(PsiModifierList, Set<String>)} instead.
   *
   * @param modifierList Modifier List that will have mosifiers augmented
   * @return true if supported and therefore may be passed to {@link #transformModifiers(PsiModifierList, Set<String>)}, false otherwise
   */
  boolean isSupported(@NotNull PsiModifierList modifierList);

  /**
   * Compute modification of  response for {@link com.intellij.psi.augment.PsiAugmentProvider#transformModifiers(PsiModifierList, Set<String>)}.
   *
   * @param modifierList Modifier List that will have mosifiers augmented
   * @param modifiers    Set of modifiers that is currently present for the list
   */
  void transformModifiers(@NotNull PsiModifierList modifierList, @NotNull final Set<String> modifiers);
}
