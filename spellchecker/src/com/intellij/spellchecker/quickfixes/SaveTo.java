// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptorUtil;
import com.intellij.ide.DataManager;
import com.intellij.model.SideEffectGuard;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.DictionaryLayer;
import com.intellij.spellchecker.DictionaryLayersProvider;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import icons.SpellcheckerIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class SaveTo implements SpellCheckerQuickFix, LowPriorityAction {
  private static final String DICTIONARY = " dictionary";
  private static final String DOTS = "...";
  @Nullable private DictionaryLayer myLevel = null;
  private String myWord;

  public SaveTo(@NotNull DictionaryLayer level) {
    myLevel = level;
  }

  public SaveTo(String word) {
    myWord = word;
  }

  public SaveTo(String word, @NotNull DictionaryLayer level) {
    myWord = word;
    myLevel = level;
  }

  @Override
  public @NotNull String getName() {
    return SpellCheckerBundle.message("save.0.to.1", myWord != null ? SpellCheckerBundle.message("0.in.quotes", myWord) : "");
  }

  @Override
  public @NotNull String getFamilyName() {
    final String dictionary = myLevel != null ? myLevel.getName() + DICTIONARY : DOTS;
    return SpellCheckerBundle.message("save.0.to.1", "", dictionary);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    DataManager.getInstance()
      .getDataContextFromFocusAsync()
      .onSuccess(context -> {
        final String wordToSave = myWord != null ? myWord : ProblemDescriptorUtil.extractHighlightedText(descriptor, descriptor.getPsiElement());
        if (myLevel == null) {
          final JBList<String> dictList = new JBList<>(
            ContainerUtil.map(DictionaryLayersProvider.Companion.getAllLayers(project), it -> it.getName())
          );

          JBPopupFactory.getInstance()
            .createListPopupBuilder(dictList)
            .setTitle(SpellCheckerBundle.message("select.dictionary.title"))
            .setItemChosenCallback(
              () ->
                CommandProcessor.getInstance().executeCommand(
                  project,
                  () -> acceptWord(wordToSave, DictionaryLayersProvider.Companion.getLayer(project, dictList.getSelectedValue()), descriptor),
                  getName(),
                  null
                )
            )
            .createPopup()
            .showInBestPositionFor(context);
        }
        else {
          acceptWord(wordToSave, myLevel, descriptor);
        }
      });
  }

  private static void acceptWord(String word, @Nullable DictionaryLayer level, ProblemDescriptor descriptor) {
    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.SETTINGS);

    PsiElement psi = descriptor.getPsiElement();
    PsiFile file = psi.getContainingFile();
    Project project = file.getProject();
    SpellCheckerManager.getInstance(project).acceptWordAsCorrect$intellij_spellchecker(word, file.getViewProvider().getVirtualFile(), project, level);

    TextRange range = descriptor.getTextRangeInElement().shiftRight(psi.getTextRange().getStartOffset());
    UpdateHighlightersUtil.removeHighlightersWithExactRange(file.getViewProvider().getDocument(), project, range);
  }

  @Override
  public Icon getIcon(int flags) {
    return SpellcheckerIcons.Spellcheck;
  }

  public static String getFixName() {
    return SpellCheckerBundle.message("save.0.to.1", "", DOTS);
  }
}
