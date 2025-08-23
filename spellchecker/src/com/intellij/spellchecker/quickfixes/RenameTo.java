// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInsight.intention.EventTrackingIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.statistics.SpellcheckerActionStatistics;
import com.intellij.spellchecker.statistics.SpellcheckerRateTracker;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import icons.SpellcheckerIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class RenameTo extends PsiUpdateModCommandQuickFix implements Iconable, EventTrackingIntentionAction {

  private final String typo;
  private final TextRange range;
  private final SmartPsiElementPointer<PsiElement> pointer;
  private final SpellcheckerRateTracker tracker;
  private final List<String> suggestions = new ArrayList<>();

  public RenameTo(String typo, TextRange range, PsiElement psi, SpellcheckerRateTracker tracker) {
    this.typo = typo;
    this.range = range;
    this.pointer = SmartPointerManager.getInstance(psi.getProject()).createSmartPsiElementPointer(psi, psi.getContainingFile());
    this.tracker = tracker;
  }

  @Override
  public @NotNull String getFamilyName() {
    return getFixName();
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement psiElement, @NotNull ModPsiUpdater updater) {
    var name = getPresentationName(psiElement);
    if (name == null) return;
    generateSuggestions(name.second, psiElement);
    updater.rename(name.first, psiElement, suggestions);
    if (!IntentionPreviewUtils.isIntentionPreviewActive()) {
      SpellcheckerActionStatistics.renameToPerformed(tracker, suggestions.size());
    }
  }

  @Override
  public void suggestionShown(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    if (tracker.markShown()) {
      SpellcheckerActionStatistics.suggestionShown(tracker);
    }
  }

  public static @Nls String getFixName() {
    return SpellCheckerBundle.message("rename.to");
  }

  @Override
  public Icon getIcon(int flags) {
    return SpellcheckerIcons.Spellcheck;
  }

  @Nullable
  private static Pair<PsiNamedElement, String> getPresentationName(PsiElement element) {
    PsiNamedElement namedElement = PsiTreeUtil.getNonStrictParentOfType(element, PsiNamedElement.class);
    if (namedElement == null) return null;
    String name =
      namedElement instanceof PsiNamedElementWithCustomPresentation custom ? custom.getPresentationName() : namedElement.getName();
    if (name == null) return null;
    return new Pair<>(namedElement, name);
  }

  private void generateSuggestions(String name, PsiElement element) {
    if (suggestions.isEmpty()) {
      TextRange range = this.range.shiftLeft(element.getText().indexOf(name));
      SpellCheckerManager.getInstance(pointer.getProject()).getSuggestions(typo)
        .stream()
        .map(suggestion -> range.replace(name, suggestion))
        .filter(suggestion -> RenameUtil.isValidName(element.getProject(), element, suggestion))
        .forEach(suggestions::add);
    }
  }
}