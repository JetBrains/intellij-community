package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.actionSystem.Anchor;
import com.intellij.openapi.project.Project;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.inspections.SpellCheckerQuickFix;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import org.jetbrains.annotations.NotNull;


public class AcceptWordAsCorrect implements SpellCheckerQuickFix {
  private String word;

  public AcceptWordAsCorrect(String word) {
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
    SpellCheckerManager spellCheckerManager = SpellCheckerManager.getInstance(project);
    spellCheckerManager.acceptWordAsCorrect(word);
  }
}
