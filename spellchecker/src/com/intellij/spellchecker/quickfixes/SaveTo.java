// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.DictionaryLevel;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.ui.components.JBList;
import icons.SpellcheckerIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

public final class SaveTo implements SpellCheckerQuickFix, LowPriorityAction {
  private static final SaveTo SAVE_TO_APP_FIX = new SaveTo(DictionaryLevel.APP);
  private static final SaveTo SAVE_TO_PROJECT_FIX = new SaveTo(DictionaryLevel.PROJECT);
  private static final String DICTIONARY = " dictionary";
  private static final String DOTS = "...";
  private DictionaryLevel myLevel = DictionaryLevel.NOT_SPECIFIED;
  private String myWord;

  private SaveTo(@NotNull DictionaryLevel level) {
    myLevel = level;
  }

  public SaveTo(String word) {
    myWord = word;
  }

  public SaveTo(String word, @NotNull DictionaryLevel level) {
    myWord = word;
    myLevel = level;
  }

  @Override
  @NotNull
  public String getName() {
    return SpellCheckerBundle.message("save.0.to.1", myWord != null ? SpellCheckerBundle.message("0.in.quotes", myWord) : "");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    final String dictionary = myLevel != DictionaryLevel.NOT_SPECIFIED ? myLevel.getName() + DICTIONARY : DOTS;
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
        final VirtualFile file = descriptor.getPsiElement().getContainingFile().getVirtualFile();
        if (myLevel == DictionaryLevel.NOT_SPECIFIED) {
          final List<String> dictionaryList = Arrays.asList(DictionaryLevel.PROJECT.getName(), DictionaryLevel.APP.getName());
          final JBList<String> dictList = new JBList<>(dictionaryList);

          JBPopupFactory.getInstance()
            .createListPopupBuilder(dictList)
            .setTitle(SpellCheckerBundle.message("select.dictionary.title"))
            .setItemChosenCallback(
              () ->
                CommandProcessor.getInstance().executeCommand(
                  project,
                  () -> acceptWord(wordToSave, DictionaryLevel.getLevelByName(dictList.getSelectedValue()), descriptor),
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

  private static void acceptWord(String word, DictionaryLevel level, ProblemDescriptor descriptor) {
    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.SETTINGS);

    PsiElement psi = descriptor.getPsiElement();
    PsiFile file = psi.getContainingFile();
    Project project = file.getProject();
    SpellCheckerManager.getInstance(project).acceptWordAsCorrect$intellij_spellchecker(word, file.getViewProvider().getVirtualFile(), project, level);

    TextRange range = descriptor.getTextRangeInElement().shiftRight(psi.getTextRange().getStartOffset());
    UpdateHighlightersUtil.removeHighlightersWithExactRange(file.getViewProvider().getDocument(), project, range);
  }

  public static SaveTo getSaveToLevelFix(DictionaryLevel level) {
    return DictionaryLevel.PROJECT == level ? SAVE_TO_PROJECT_FIX : SAVE_TO_APP_FIX;
  }

  @Override
  public Icon getIcon(int flags) {
    return SpellcheckerIcons.Spellcheck;
  }

  public static String getFixName() {
    return SpellCheckerBundle.message("save.0.to.1", "", DOTS);
  }
}
