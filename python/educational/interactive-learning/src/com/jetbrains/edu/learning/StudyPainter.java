package com.jetbrains.edu.learning;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.markup.*;
import com.intellij.ui.JBColor;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

public class StudyPainter {
  private StudyPainter() {

  }

  /**
   * Draw task window with color according to its status
   */
  public static void drawAnswerPlaceholder(@NotNull final Editor editor, AnswerPlaceholder answerPlaceholder) {
    Document document = editor.getDocument();
    if (!answerPlaceholder.isValid(document)) {
      return;
    }
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    final TextAttributes defaultTestAttributes = new TextAttributes(scheme.getDefaultForeground(), scheme.getDefaultBackground(), null,
                                                                    EffectType.BOXED, Font.PLAIN);
    final JBColor color = answerPlaceholder.getColor();
    int startOffset = answerPlaceholder.getRealStartOffset(document);
    RangeHighlighter
      highlighter = editor.getMarkupModel().addRangeHighlighter(startOffset, startOffset + answerPlaceholder.getLength(), HighlighterLayer.LAST + 1,
                                                                defaultTestAttributes, HighlighterTargetArea.EXACT_RANGE);
    highlighter.setCustomRenderer(new CustomHighlighterRenderer() {
      @Override
      public void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics g) {
        g.setColor(color);
        Point point = editor.logicalPositionToXY(editor.offsetToLogicalPosition(highlighter.getStartOffset()));
        Point pointEnd = editor.logicalPositionToXY(editor.offsetToLogicalPosition(highlighter.getEndOffset()));
        g.drawRect(point.x, point.y - 2, (pointEnd.x - point.x), editor.getLineHeight() + 1);
      }
    });
    editor.getCaretModel().moveToOffset(startOffset);
    highlighter.setGreedyToLeft(true);
    highlighter.setGreedyToRight(true);
  }


  public static void drawAllWindows(Editor editor, TaskFile taskFile) {
    editor.getMarkupModel().removeAllHighlighters();
    for (AnswerPlaceholder answerPlaceholder : taskFile.getAnswerPlaceholders()) {
      drawAnswerPlaceholder(editor, answerPlaceholder);
    }
    final Document document = editor.getDocument();
    EditorActionManager.getInstance()
      .setReadonlyFragmentModificationHandler(document, new TaskWindowDeleteHandler(editor));
    createGuardedBlocks(editor, taskFile);
    editor.getColorsScheme().setColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR, null);
  }


  /**
   * Marks symbols adjacent to task windows as read-only fragments
   */
  public static void createGuardedBlocks(@NotNull final Editor editor, TaskFile taskFile) {
    final Document document = editor.getDocument();
    if (document instanceof DocumentImpl) {
      DocumentImpl documentImpl = (DocumentImpl)document;
      List<RangeMarker> blocks = documentImpl.getGuardedBlocks();
      for (AnswerPlaceholder answerPlaceholder : taskFile.getAnswerPlaceholders()) {
        if (!answerPlaceholder.isValid(document)) {
          return;
        }
        int start = answerPlaceholder.getRealStartOffset(document);
        int end = start + answerPlaceholder.getLength();
        if (start != 0) {
          createGuardedBlock(editor, blocks, start - 1, start);
        }
        if (end != document.getTextLength()) {
          createGuardedBlock(editor, blocks, end, end + 1);
        }
      }
    }
  }

  private static void createGuardedBlock(Editor editor, List<RangeMarker> blocks, int start, int end) {
    RangeHighlighter rh = editor.getMarkupModel()
      .addRangeHighlighter(start, end, HighlighterLayer.LAST + 1, null, HighlighterTargetArea.EXACT_RANGE);
    blocks.add(rh);
  }


}
