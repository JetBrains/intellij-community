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
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.ui.components.JBList;
import com.intellij.util.Consumer;
import icons.SpellcheckerIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

import static com.intellij.spellchecker.SpellCheckerManager.APP;
import static com.intellij.spellchecker.SpellCheckerManager.PROJECT;


public class SaveTo implements SpellCheckerQuickFix {
  private String myWord;

  public static final String FIX_NAME = SpellCheckerBundle.message("add.to");

  public SaveTo(String word) {
    myWord = word;
  }

  @NotNull
  public String getName() {
    return myWord != null ? SpellCheckerBundle.message("add.0.to", myWord) : SpellCheckerBundle.message("add.to");
  }

  @NotNull
  public String getFamilyName() {
    return SpellCheckerBundle.message("add.to");
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
      final List<String> dictionaryList = Arrays.asList(PROJECT, APP);
      if (myWord == null) {
        myWord = ProblemDescriptorUtil.extractHighlightedText(descriptor, descriptor.getPsiElement());
      }
      final JBList<String> dictList = new JBList<>(dictionaryList);
      JBPopupFactory.getInstance()
        .createListPopupBuilder(dictList)
        .setTitle(SpellCheckerBundle.message("select.dictionary.title"))
        .setItemChoosenCallback(() -> spellCheckerManager.acceptWordAsCorrect(myWord, project, dictList.getSelectedValue()))
        .createPopup()
        .showInBestPositionFor(context);
    });
  }


  public Icon getIcon(int flags) {
    return SpellcheckerIcons.Spellcheck;
  }
}
