// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptorUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.Anchor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.SpellCheckerManager.DictionaryLevel;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.ui.components.JBList;
import com.intellij.util.Consumer;
import icons.SpellcheckerIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

import static com.intellij.spellchecker.SpellCheckerManager.DictionaryLevel.getLevelByName;

public class SaveTo implements SpellCheckerQuickFix {
  private static final SaveTo SAVE_TO_APP_FIX = new SaveTo(DictionaryLevel.APP);
  private static final SaveTo SAVE_TO_PROJECT_FIX = new SaveTo(DictionaryLevel.PROJECT);
  private DictionaryLevel myLevel = DictionaryLevel.NOT_SPECIFIED;
  private String myWord;

  public static final String FIX_NAME = SpellCheckerBundle.message("add.to");

  private SaveTo(DictionaryLevel level) {
    myLevel = level;
  }

  public SaveTo(String word) {
    myWord = word;
  }

  @NotNull
  public String getName() {
    String name = myWord != null ? SpellCheckerBundle.message("add.0.to", myWord) : SpellCheckerBundle.message("add.to");
    if (myLevel != DictionaryLevel.NOT_SPECIFIED) name += myLevel.getName();
    return name;
  }

  @NotNull
  public String getFamilyName() {
    if (myLevel == DictionaryLevel.NOT_SPECIFIED) {
      return SpellCheckerBundle.message("add.to");
    }
    return SpellCheckerBundle.message("save.to.0", myLevel.getName());
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
    final AsyncResult<DataContext> asyncResult = DataManager.getInstance().getDataContextFromFocus();
    asyncResult.doWhenDone((Consumer<DataContext>)context -> {
      final SpellCheckerManager spellCheckerManager = SpellCheckerManager.getInstance(project);
      if (myWord == null) {
        myWord = ProblemDescriptorUtil.extractHighlightedText(descriptor, descriptor.getPsiElement());
      }
      if (myLevel == DictionaryLevel.NOT_SPECIFIED) {
        final List<String> dictionaryList = Arrays.asList(DictionaryLevel.PROJECT.getName(), DictionaryLevel.APP.getName());
        final JBList<String> dictList = new JBList<>(dictionaryList);
        JBPopupFactory.getInstance()
          .createListPopupBuilder(dictList)
          .setTitle(SpellCheckerBundle.message("select.dictionary.title"))
          .setItemChoosenCallback(
            () -> spellCheckerManager.acceptWordAsCorrect(myWord, project, getLevelByName(dictList.getSelectedValue()), true))
          .createPopup()
          .showInBestPositionFor(context);
      }
      else {
        spellCheckerManager.acceptWordAsCorrect(myWord, project, myLevel, false);
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
