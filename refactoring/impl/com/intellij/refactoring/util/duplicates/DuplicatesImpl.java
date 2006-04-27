/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.util.duplicates;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public class DuplicatesImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.duplicates.DuplicatesImpl");

  private DuplicatesImpl() {}

  public static void invoke(final Project project, Editor editor, final MatchProvider provider) {
    final List<Match> duplicates = provider.getDuplicates();
    for (final Match match : duplicates) {
      final ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
      highlightMatch(project, editor, match, highlighters);
      final TextRange textRange = match.getTextRange();
      final LogicalPosition logicalPosition = editor.offsetToLogicalPosition(textRange.getStartOffset());
      expandAllRegionsCoveringRange(project, editor, textRange);
      editor.getScrollingModel().scrollTo(logicalPosition, ScrollType.MAKE_VISIBLE);
      String prompt = provider.getConfirmDuplicatePrompt(match);
      final int matchAnswer = Messages.showYesNoCancelDialog(project, prompt,
          RefactoringBundle.message("process.duplicates.title"), Messages.getQuestionIcon());
      HighlightManager.getInstance(project).removeSegmentHighlighter(editor, highlighters.get(0));
      if (matchAnswer == 0) {
        final Runnable action = new Runnable() {
          public void run() {
            try {
              provider.processMatch(match);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        };

        //use outer command
        ApplicationManager.getApplication().runWriteAction(action);
      } else if (matchAnswer == 2) {
        break;
      }
    }
  }

  private static void expandAllRegionsCoveringRange(final Project project, Editor editor, final TextRange textRange) {
    final FoldRegion[] foldRegions = CodeFoldingManager.getInstance(project).getFoldRegionsAtOffset(editor, textRange.getStartOffset());
    boolean anyCollapsed = false;
    for (final FoldRegion foldRegion : foldRegions) {
      if (!foldRegion.isExpanded()) {
        anyCollapsed = true;
        break;
      }
    }
    if (anyCollapsed) {
      editor.getFoldingModel().runBatchFoldingOperation(new Runnable() {
        public void run() {
          for (final FoldRegion foldRegion : foldRegions) {
            if (!foldRegion.isExpanded()) {
              foldRegion.setExpanded(true);
            }
          }
        }
      });
    }
  }

  public static void highlightMatch(final Project project, Editor editor, final Match match, final ArrayList<RangeHighlighter> highlighters) {
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    HighlightManager.getInstance(project).addRangeHighlight(editor, match.getTextRange().getStartOffset(), match.getTextRange().getEndOffset(),
                                                            attributes, true, highlighters);
  }

  public static void processDuplicates(final MatchProvider provider, final Project project, Editor editor) {
    boolean hasDuplicates = provider.hasDuplicates();
    if (hasDuplicates) {
      final int answer = Messages.showYesNoDialog(project,
        RefactoringBundle.message("0.has.detected.1.code.fragments.in.this.file.that.can.be.replaced.with.a.call.to.extracted.method",
        ApplicationNamesInfo.getInstance().getProductName(), provider.getDuplicates().size()),
        RefactoringBundle.message("process.duplicates.title"), Messages.getQuestionIcon());
      if (answer == 0) {
        invoke(project, editor, provider);
      }
    }
  }
}
