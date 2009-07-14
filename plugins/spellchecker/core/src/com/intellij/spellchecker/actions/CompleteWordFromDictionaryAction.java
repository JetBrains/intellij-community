package com.intellij.spellchecker.actions;


import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.spellchecker.SpellCheckerManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Completion action for spell checker.
 */
public final class CompleteWordFromDictionaryAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {


    Project project = e.getData(PlatformDataKeys.PROJECT);
    Editor editor = e.getData(PlatformDataKeys.EDITOR);
    PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);

    if (project != null && editor != null && psiFile != null) {
      // Get position before caret
      int caretOffset = editor.getCaretModel().getOffset();

      int documentOffset = caretOffset - 1;

      if (documentOffset > 0) {
        StringBuilder prefixBuilder = new StringBuilder();
        CharSequence charSequence = editor.getDocument().getCharsSequence();
        for (char c; documentOffset >= 0 && Character.isJavaIdentifierPart(c = charSequence.charAt(documentOffset)); documentOffset--) {
          prefixBuilder.append(c);
        }
        documentOffset = caretOffset;
        StringBuilder suffixBuilder = new StringBuilder();
        for (char c;
             documentOffset < charSequence.length() && Character.isJavaIdentifierPart(c = charSequence.charAt(documentOffset));
             documentOffset++) {
          suffixBuilder.append(c);
        }

        if (prefixBuilder.length() > 0) {
          String[] prefixes = NameUtil.nameToWords(prefixBuilder.reverse().toString());
          String prefix = prefixes.length > 0 ? prefixes[prefixes.length - 1] : "";
          if (prefix.length() > 0) {
            String[] suffixes = NameUtil.nameToWords(suffixBuilder.toString());
            String suffix = suffixes.length > 0 ? suffixes[0] : "";
            if (suffix.length() > 0 && Character.isLowerCase(suffix.charAt(0))) {
              // Select replacement part
              editor.getSelectionModel().setSelection(caretOffset, caretOffset + suffix.length());
            }
            List<String> variants = SpellCheckerManager.getInstance(project).getVariants(prefix);

            List<LookupItem<String>> lookupItems = new ArrayList<LookupItem<String>>();
            for (String variant : variants) {
              lookupItems.add(new LookupItem<String>(variant, variant));
            }

            LookupItem[] items = new LookupItem[lookupItems.size()];
            items = lookupItems.toArray(items);
            LookupManager lookupManager = LookupManager.getInstance(project);
            lookupManager.showLookup(editor, items, prefix, null);

            /*AnnotationHolder.createInfoAnnotation(TextRange.from(0, prefix.length()), "test");*/
          }
        }
      }
    }
  }

  public void update(AnActionEvent e) {
    super.update(e);
    Project project = e.getData(LangDataKeys.PROJECT);
    Editor editor = e.getData(LangDataKeys.EDITOR);
    PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
    boolean available = project != null && editor != null && psiFile != null;
    Presentation presentation = e.getPresentation();
    if (presentation.isVisible()) {
      presentation.setVisible(available);
    }
    if (presentation.isEnabled()) {
      presentation.setEnabled(available);
    }
  }

  /* private static final class LookupItemPreferencePolicyImpl implements LookupItemPreferencePolicy {
      public void setPrefix(String prefix) {
      }

      public void itemSelected(LookupItem lookupItem) {
      }

      public int compare(LookupItem o1, LookupItem o2) {
          return o1.getLookupString().compareTo(o2.getLookupString());
      }
  }*/
}
