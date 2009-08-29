package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.Anchor;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.quickfixes.SpellCheckerQuickFix;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ChangeTo implements SpellCheckerQuickFix {

  private TextRange textRange;
  private String word;
  private Project project;

  public ChangeTo(@NotNull TextRange textRange, @NotNull String word, @NotNull Project project) {
    this.textRange = textRange;
    this.word = word;
    this.project = project;
  }

  @NotNull
  public String getName() {
   return SpellCheckerBundle.message("change.to");
  }

  @NotNull
  public String getFamilyName() {
    return SpellCheckerBundle.message("change.to");
  }

  @NotNull
  public Anchor getPopupActionAnchor() {
    return Anchor.FIRST;
  }

 
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {

    final Editor editor = PlatformDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext());
    if (editor == null) {
      return;
    }

    int psiElementOffset = descriptor.getPsiElement().getTextRange().getStartOffset();
    editor.offsetToLogicalPosition(psiElementOffset + textRange.getStartOffset());
    editor.getSelectionModel().setSelection(psiElementOffset + textRange.getStartOffset(), psiElementOffset + textRange.getEndOffset());

    String word = editor.getSelectionModel().getSelectedText();

    if (word == null || StringUtil.isEmpty(word)) {
      return;
    }

    SpellCheckerManager manager = SpellCheckerManager.getInstance(project);
    List<String> variants = manager.getSuggestions(word);

    List<LookupItem<String>> lookupItems = new ArrayList<LookupItem<String>>();
    for (String variant : variants) {
      final LookupItem<String> lookupItem = new LookupItem<String>(variant, variant);
      lookupItems.add(lookupItem);
    }
    LookupItem[] items = new LookupItem[lookupItems.size()];
    items = lookupItems.toArray(items);
    LookupManager lookupManager = LookupManager.getInstance(project);
    lookupManager.showLookup(editor, items, null);


  }


}
