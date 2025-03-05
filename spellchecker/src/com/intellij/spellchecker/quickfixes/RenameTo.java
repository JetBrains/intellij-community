// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.quickfixes;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiNamedElementWithCustomPresentation;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import icons.SpellcheckerIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class RenameTo extends PsiUpdateModCommandQuickFix implements Iconable {
  @Override
  public @NotNull String getFamilyName() {
    return getFixName();
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement psiElement, @NotNull ModPsiUpdater updater) {
    PsiNamedElement named = PsiTreeUtil.getNonStrictParentOfType(psiElement, PsiNamedElement.class);
    if (named == null) return;
    String name = named instanceof PsiNamedElementWithCustomPresentation custom ? custom.getPresentationName() : named.getName();
    if (name == null) return;
    List<String> names = SpellCheckerManager.getInstance(project).getSuggestions(name)
      .stream()
      .filter(suggestion -> RenameUtil.isValidName(project, psiElement, suggestion))
      .toList();
    updater.rename(named, psiElement, names);
  }
  
  public static @Nls String getFixName() {
    return SpellCheckerBundle.message("rename.to");
  }

  @Override
  public Icon getIcon(int flags) {
    return SpellcheckerIcons.Spellcheck;
  }
}