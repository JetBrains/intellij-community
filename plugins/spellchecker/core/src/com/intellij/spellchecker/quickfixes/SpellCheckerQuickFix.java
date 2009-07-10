package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.actionSystem.Anchor;
import org.jetbrains.annotations.NotNull;

/**
 * Spell checker quick fix.
 */
public interface SpellCheckerQuickFix extends LocalQuickFix {
  /**
   * Return anchor for actions. Basically return {@link com.intellij.openapi.actionSystem.Anchor#FIRST} for common
   * suggest words, and {@link com.intellij.openapi.actionSystem.Anchor#LAST} for 'Add to ...' actions.
   *
   * @return Action anchor
   */
  @NotNull
  Anchor getPopupActionAnchor();
}
