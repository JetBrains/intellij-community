// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptorUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/**
 * General quickfix that accepts word as correct
 * (not undoable, no possibility to choose dictionary)
 *
 * @see SaveTo for undoable quickfix with dictionary chooser
 */
public class AcceptWordAsCorrect implements SpellCheckerQuickFix {
  private String myWord;

  public AcceptWordAsCorrect(String word) {
    myWord = word;
  }

  public AcceptWordAsCorrect() {
  }

  @Override
  public @NotNull String getName() {
    return myWord != null ? SpellCheckerBundle.message("add.0.to.dictionary", myWord) : SpellCheckerBundle.message("add.to.dictionary");
  }

  @Override
  public @NotNull String getFamilyName() {
    return SpellCheckerBundle.message("add.to.dictionary");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    SpellCheckerManager manager = SpellCheckerManager.getInstance(project);
    if (myWord != null) {
      manager.acceptWordAsCorrect(myWord, project);
    }
    else {
      manager.acceptWordAsCorrect(ProblemDescriptorUtil.extractHighlightedText(descriptor, descriptor.getPsiElement()), project);
    }
  }

  @Override
  public Icon getIcon(int flags) {
    return AllIcons.Actions.AddToDictionary;
  }
}
