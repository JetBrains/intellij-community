package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.actionSystem.Anchor;
import com.intellij.openapi.project.Project;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.inspections.SpellCheckerQuickFix;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import org.jetbrains.annotations.NotNull;

/**
 * Add to dictionary quick fix.
 *
 * @author Sergiy Dubovik
 */
public class AddToDictionaryQuickFix implements SpellCheckerQuickFix {
  private String word;

  public AddToDictionaryQuickFix(String word) {
    this.word = word;
  }

  @NotNull
  public String getName() {
    return SpellCheckerBundle.message("add.0.to.dictionary", word);
  }

  @NotNull
  public String getFamilyName() {
    return SpellCheckerBundle.message("spelling");
  }

  @NotNull
  public Anchor getPopupActionAnchor() {
    return Anchor.LAST;
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    SpellCheckerManager spellCheckerManager = SpellCheckerManager.getInstance();
    spellCheckerManager.addToDictionary(word);
  }
}
