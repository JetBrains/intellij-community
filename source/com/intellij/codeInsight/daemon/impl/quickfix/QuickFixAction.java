package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.IntentionActionComposite;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexey Kudravtsev
 */
public final class QuickFixAction extends IntentionActionComposite {
  protected void addAvailableActions(HighlightInfo info, Editor editor, PsiFile file, List<IntentionAction> list) {
    addAvailableActionsForGroups(info, editor, file, list, UpdateHighlightersUtil.NORMAL_HIGHLIGHT_GROUPS );
  }

  public static void registerQuickFixAction(HighlightInfo info, IntentionAction action) {
    registerQuickFixAction(info, null, action);
  }

  public static void registerQuickFixAction(HighlightInfo info, TextRange fixRange, IntentionAction action) {
    if (info == null || action == null) return;
    if (fixRange == null) fixRange = new TextRange(info.startOffset, info.endOffset);
    if (info.quickFixActionRanges == null) {
      info.quickFixActionRanges = new ArrayList<Pair<IntentionAction, TextRange>>();
    }
    info.quickFixActionRanges.add(new Pair<IntentionAction, TextRange> (action, fixRange));
    info.fixStartOffset = Math.min (info.fixStartOffset, fixRange.getStartOffset());
    info.fixEndOffset = Math.max (info.fixEndOffset, fixRange.getEndOffset());
  }

  // spoil current document in order to Undo action work
  public static void spoilDocument(Project project, PsiFile file) {
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    document.insertString(0, " ");
    document.deleteString(0, 1);
    PsiDocumentManager.getInstance(project).commitAllDocuments();
  }
}