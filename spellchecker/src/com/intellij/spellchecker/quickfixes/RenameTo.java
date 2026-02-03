// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInsight.intention.EventTrackingIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.refactoring.rename.*;
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

public class RenameTo extends IntentionAndQuickFixAction implements Iconable, EventTrackingIntentionAction {

  private final String typo;
  private final SmartPsiFileRange rangeRelativeToFile;
  private final SmartPsiElementPointer<PsiElement> pointer;
  private final SpellcheckerRateTracker tracker;
  private volatile List<String> suggestions;
  private SmartPsiElementPointer<PsiElement> namedPointer;

  public RenameTo(String typo, TextRange range, PsiElement psi, SpellcheckerRateTracker tracker) {
    PsiFile file = psi.getContainingFile();
    this.rangeRelativeToFile = SmartPointerManager.getInstance(psi.getProject())
      .createSmartPsiFileRangePointer(file, range.shiftRight(psi.getTextRange().getStartOffset()));
    this.typo = typo;
    this.pointer = SmartPointerManager.getInstance(psi.getProject()).createSmartPsiElementPointer(psi, file);
    this.tracker = tracker;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, @Nullable Editor editor, PsiFile psiFile) {
    PsiElement element = pointer.getElement();
    if (element == null) return false;
    var presentationName = getPresentationName(element);
    if (presentationName == null) return false;
    generateSuggestions(presentationName.getSecond(), element);
    this.namedPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(presentationName.getFirst());
    if (suggestions == null || suggestions.isEmpty()) return false;
    return true;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull String getName() {
    return getFixName(suggestions);
  }

  @Override
  public @NotNull String getFamilyName() {
    return getFixName(suggestions);
  }

  @Override
  public void applyFix(@NotNull Project project, PsiFile psiFile, @Nullable Editor editor) {
    PsiElement element = namedPointer.getElement() == null ? null : namedPointer.getElement();
    if (element == null) return;

    if (suggestions.size() == 1) {
      runRenamer(element, suggestions.getFirst());
    }
    else {
      var context = DataManager.getInstance().getDataContext(editor.getContentComponent());
      DataContext contextWithSuggestions = dataId -> {
        if (PsiElementRenameHandler.NAME_SUGGESTIONS.is(dataId)) return new ArrayList<>(suggestions);
        if (PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) return new PsiElement[]{element};
        return context.getData(dataId);
      };
      RefactoringActionHandler handler = getRenameHandler(contextWithSuggestions);
      handler.invoke(project, editor, psiFile, contextWithSuggestions);
    }

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

  public static @Nls String getFixName(List<String> suggestions) {
    return (suggestions != null && suggestions.size() == 1) ?
           SpellCheckerBundle.message("rename.to.0", suggestions.getFirst()) :
           SpellCheckerBundle.message("rename.to");
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

  private static RefactoringActionHandler getRenameHandler(DataContext dataContext) {
    RenameHandler handler = RenameHandlerRegistry.getInstance().getRenameHandler(dataContext);
    if (handler == null) return RefactoringActionHandlerFactory.getInstance().createRenameHandler();
    return handler;
  }

  private void generateSuggestions(String name, PsiElement element) {
    if (suggestions == null) {
      TextRange range = restoreRange();
      if (range == null) return;
      this.suggestions = SpellCheckerManager.getInstance(pointer.getProject()).getSuggestions(typo)
        .stream()
        .map(suggestion -> range.replace(name, suggestion))
        .filter(suggestion -> RenameUtil.isValidName(element.getProject(), element, suggestion))
        .distinct()
        .toList();
    }
  }

  private @Nullable TextRange restoreRange() {
    PsiElement element = pointer.getElement();
    Segment rangeRelativeToFile = this.rangeRelativeToFile.getRange();
    if (element == null || rangeRelativeToFile == null) return null;

    return TextRange.create(rangeRelativeToFile)
      .shiftLeft(element.getTextRange().getStartOffset());
  }

  private void runRenamer(PsiElement element, String suggestion) {
    new RenameProcessor(pointer.getProject(), element, suggestion, true, true).run();
  }
}