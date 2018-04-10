// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.Anchor;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.SpellCheckerManager.DictionaryLevel;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.ui.components.JBList;
import icons.SpellcheckerIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

import static com.intellij.codeInspection.ProblemDescriptorUtil.extractHighlightedText;
import static com.intellij.spellchecker.SpellCheckerManager.DictionaryLevel.getLevelByName;

public class SaveTo implements SpellCheckerQuickFix, LowPriorityAction {
  private static final SaveTo SAVE_TO_APP_FIX = new SaveTo(DictionaryLevel.APP);
  private static final SaveTo SAVE_TO_PROJECT_FIX = new SaveTo(DictionaryLevel.PROJECT);
  private static final String DICTIONARY = " dictionary";
  private static final String DOTS = "...";
  private DictionaryLevel myLevel = DictionaryLevel.NOT_SPECIFIED;
  private String myWord;

  public static final String FIX_NAME = SpellCheckerBundle.message("save.0.to.1", "", DOTS);

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

  @NotNull
  public String getName() {
    final String dictionary = myLevel != DictionaryLevel.NOT_SPECIFIED ? myLevel.getName() + DICTIONARY : DOTS;
    final String word = myWord != null ? SpellCheckerBundle.message("0.in.qoutes", myWord) : "";
    return SpellCheckerBundle.message("save.0.to.1", word, dictionary);
  }

  @NotNull
  public String getFamilyName() {
    final String dictionary = myLevel != DictionaryLevel.NOT_SPECIFIED ? myLevel.getName() + DICTIONARY : DOTS;
    return SpellCheckerBundle.message("save.0.to.1", "", dictionary);
  }

  @NotNull
  public Anchor getPopupActionAnchor() {
    return Anchor.LAST;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    DataManager.getInstance()
               .getDataContextFromFocusAsync()
               .onSuccess(context -> {
                 final SpellCheckerManager spellCheckerManager = SpellCheckerManager.getInstance(project);
                 final String wordToSave = myWord != null ? myWord : extractHighlightedText(descriptor, descriptor.getPsiElement());
                 final VirtualFile file = descriptor.getPsiElement().getContainingFile().getVirtualFile();
                 if (myLevel == DictionaryLevel.NOT_SPECIFIED) {
                   final List<String> dictionaryList = Arrays.asList(DictionaryLevel.PROJECT.getName(), DictionaryLevel.APP.getName());
                   final JBList<String> dictList = new JBList<>(dictionaryList);
                   JBPopupFactory.getInstance()
                                 .createListPopupBuilder(dictList)
                                 .setTitle(SpellCheckerBundle.message("select.dictionary.title"))
                                 .setItemChoosenCallback(
                                   () -> CommandProcessor.getInstance().executeCommand(project, () -> spellCheckerManager
                                                                                         .acceptWordAsCorrect(wordToSave, file, project, getLevelByName(dictList.getSelectedValue())),
                                                                                       getName(), null))
                                 .createPopup()
                                 .showInBestPositionFor(context);
                 }
                 else {
                   spellCheckerManager.acceptWordAsCorrect(wordToSave, file, project, myLevel);
                 }
               });
  }

  public static SaveTo getSaveToLevelFix(DictionaryLevel level) {
    return DictionaryLevel.PROJECT == level ? SAVE_TO_PROJECT_FIX : SAVE_TO_APP_FIX;
  }

  public Icon getIcon(int flags) {
    return SpellcheckerIcons.Spellcheck;
  }
}
