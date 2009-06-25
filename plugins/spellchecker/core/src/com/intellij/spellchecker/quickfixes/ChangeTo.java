package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.actionSystem.Anchor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.inspections.SpellCheckerQuickFix;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Quick fix for misspelled words.
 *
 * @author Sergiy Dubovik
 */
public class ChangeTo implements SpellCheckerQuickFix {

  private TextRange textRange;

  public ChangeTo(@NotNull TextRange textRange) {
    this.textRange = textRange;
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

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    PsiFile psiFile = descriptor.getPsiElement().getContainingFile();
    Document document = documentManager.getDocument(psiFile);
    int psiElementOffset = descriptor.getPsiElement().getTextRange().getStartOffset();

    /*if (document != null) {
        document.replaceString(
                psiElementOffset + textRange.getStartOffset(),
                psiElementOffset + textRange.getEndOffset(),
                correctWord
        );
    }*/


    final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();

    if (editor == null) return;

    /*FileEditorManager.getInstance(project).openEditor(new OpenFileDescriptor(project, psiFile.getVirtualFile(), psiElementOffset + textRange.getStartOffset()), true);*/
    editor.offsetToLogicalPosition(psiElementOffset + textRange.getStartOffset());


    editor.getSelectionModel().setSelection(psiElementOffset + textRange.getStartOffset(), psiElementOffset + textRange.getEndOffset());

    String word = editor.getSelectionModel().getSelectedText();
    /*editor.getSelectionModel().selectWordAtCaret(true);*/

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
